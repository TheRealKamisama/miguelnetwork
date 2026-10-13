package io.github.therealkamisama.miguelnetwork.interop;

import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Negotiates only the peer's public factory, then uses JDK callbacks. */
public final class ZstdPeer {
    private static boolean lookedUp;
    private static ZstdPeer peer;
    private static RuntimeException negotiationFailure;
    private final Map<?,?> descriptor;
    private final Map<String,Object> streamOrder;

    ZstdPeer(Map<?,?> descriptor) {
        this.descriptor = descriptor;
        this.streamOrder = StreamOrder.negotiatePeer(StreamOrder.TRANSPORT, descriptor);
    }

    public static synchronized ZstdPeer get() {
        if (lookedUp) {
            if (negotiationFailure != null) throw negotiationFailure;
            return peer;
        }
        lookedUp = true;
        try {
            Class<?> factory = Class.forName("cn.tohsaka.factory.zstdnet.interop.NativeBridge");
            Object result = factory.getMethod("negotiate", int.class).invoke(null, NativeBridge.API_MAJOR);
            peer = fromDescriptor(result);
            if (peer != null) {
                MiguelNetwork.LOGGER.info("MiguelNetwork negotiated ZstdNet native API v{} for Minecraft {}",
                        NativeBridge.API_MAJOR, NativeBridge.MINECRAFT_VERSION);
            } else {
                MiguelNetwork.LOGGER.warn("ZstdNet native API/platform/order mismatch; native capability unavailable");
            }
        } catch (ClassNotFoundException absent) {
            // Standalone operation with no ZstdNet dependency.
        } catch (InvocationTargetException failure) {
            negotiationFailure = new IllegalStateException("ZstdNet native negotiation failed", failure.getCause());
            throw negotiationFailure;
        } catch (RuntimeException failure) {
            negotiationFailure = failure;
            throw failure;
        } catch (ReflectiveOperationException | LinkageError failure) {
            MiguelNetwork.LOGGER.warn("ZstdNet native API unavailable", failure);
        }
        return peer;
    }

    static ZstdPeer fromDescriptor(Object result) {
        if (!(result instanceof Map<?,?> value)
                || !Integer.valueOf(NativeBridge.API_MAJOR).equals(value.get("apiMajor"))
                || !NativeBridge.MINECRAFT_VERSION.equals(value.get("minecraftVersion"))) return null;
        ZstdPeer candidate = new ZstdPeer(value);
        return candidate.streamOrder == null ? null : candidate;
    }

    public boolean supportsStream() {
        return streamOrder != null && descriptor.get("filters") instanceof Map<?,?> filters
                && Integer.valueOf(1).equals(filters.get("zstdnet-stream"))
                && descriptor.get("openStream") instanceof Function<?,?>
                && descriptor.get("ownsEndpoint") instanceof Predicate<?>;
    }

    public boolean supportsServer() { return streamOrder != null && descriptor.get("watchServer") instanceof Function<?,?>; }

    @SuppressWarnings("unchecked")
    public boolean ownsEndpoint(InetSocketAddress address) {
        Object callback = descriptor.get("ownsEndpoint");
        return callback instanceof Predicate<?> predicate && ((Predicate<InetSocketAddress>) predicate).test(address);
    }

    @SuppressWarnings("unchecked")
    public Stream open(InetSocketAddress upstream, InetSocketAddress logical) {
        if (!supportsStream()) throw new IllegalStateException("Required native Zstd stream unavailable");
        var open = (Function<Map<String,Object>,Map<String,Object>>) descriptor.get("openStream");
        Map<String,Object> result = open.apply(Map.of("upstream", upstream, "statusUpstream", upstream,
                "presented", logical, "udpUpstream", logical, "streamOrder", streamOrder));
        if (result == null) throw new IllegalStateException("Missing native stream response");
        AutoCloseable lease = result.get("lease") instanceof AutoCloseable value ? value : null;
        try {
            if (!StreamOrder.accepted(result.get("streamOrder"))) {
                throw new IllegalStateException("Native stream response has no matching order agreement");
            }
            var address = NativeBridge.endpoint(result.get("address"), "stream address");
            if (address.getAddress() == null || !address.getAddress().isLoopbackAddress()) {
                throw new IllegalStateException("Native stream must return a resolved loopback listener");
            }
            if (lease == null || !(result.get("stats") instanceof Supplier<?>)) {
                throw new IllegalStateException("Invalid native stream lease/statistics");
            }
            Object connected = result.get("connected");
            if (connected != null && !(connected instanceof Runnable)) {
                throw new IllegalStateException("Invalid native stream connected callback");
            }
            return new Stream(address, lease, (Supplier<Map<String,Object>>) result.get("stats"),
                    connected == null ? () -> {} : (Runnable) connected);
        } catch (RuntimeException | Error failure) {
            NativeBridge.closeQuietly(lease);
            throw failure;
        }
    }

    @SuppressWarnings("unchecked")
    public AutoCloseable watchServer(Consumer<ServerState> consumer) {
        if (!(descriptor.get("watchServer") instanceof Function<?,?> callback)) {
            throw new IllegalStateException("Native Zstd server lifecycle unavailable");
        }
        var watch = (Function<Consumer<Map<String,Object>>,AutoCloseable>) callback;
        AutoCloseable subscription = watch.apply(value -> consumer.accept(ServerState.decode(value)));
        if (subscription == null) throw new IllegalStateException("Missing native server subscription");
        return subscription;
    }

    public record Stream(InetSocketAddress address, AutoCloseable lease,
                         Supplier<Map<String,Object>> stats, Runnable connected) {}

    public record ServerState(long generation, InetSocketAddress listener, InetSocketAddress backend) {
        public static ServerState inactive() { return new ServerState(-1, null, null); }
        public boolean ready() { return listener != null; }
        static ServerState decode(Map<String,Object> value) {
            if (value == null || !(value.get("generation") instanceof Long generation) || generation < 0
                    || !(value.get("ready") instanceof Boolean ready)) {
                throw new IllegalStateException("Invalid native Zstd server state");
            }
            try {
                return ready ? new ServerState(generation, NativeBridge.endpoint(value.get("listener"), "listener"),
                        NativeBridge.endpoint(value.get("backend"), "backend"))
                        : new ServerState(generation, null, value.containsKey("backend")
                                ? NativeBridge.endpoint(value.get("backend"), "backend") : null);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("Invalid native Zstd server endpoints", invalid);
            }
        }
    }
}
