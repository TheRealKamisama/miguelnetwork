package io.github.therealkamisama.miguelnetwork.interop;

import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import io.github.therealkamisama.miguelnetwork.client.ClientTunnelManager;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Public native interop API; all boundary types belong to the JDK. */
public final class NativeBridge {
    public static final int API_MAJOR = 1;
    public static final String MINECRAFT_VERSION = "1.7.10";
    private NativeBridge() {}

    public static Map<String,Object> negotiate(int requestedApiMajor) {
        if (requestedApiMajor != API_MAJOR) return null;
        return Map.of("apiMajor", API_MAJOR, "minecraftVersion", MINECRAFT_VERSION,
                "streamLayer", StreamOrder.layer(StreamOrder.TRANSPORT),
                "agreeStreamOrder", (Function<Map<String,Object>,Map<String,Object>>) request ->
                        StreamOrder.agree(StreamOrder.TRANSPORT, request),
                "prepareConnection", (Function<Map<String,Object>,Map<String,Object>>) NativeBridge::prepare);
    }

    private static Map<String,Object> prepare(Map<String,Object> request) {
        InetSocketAddress logical = endpoint(request.get("logical"), "logical");
        Object token = request.get("cancelled");
        if (token != null && !(token instanceof BooleanSupplier)) {
            throw new IllegalArgumentException("Invalid native cancellation callback");
        }
        BooleanSupplier cancelled = token == null ? () -> false : (BooleanSupplier) token;
        checkCancelled(cancelled);
        Map<String,Object> order = StreamOrder.require(request.get("streamOrder"));
        ClientTunnelManager.TunnelLease lease = ClientTunnelManager.acquire(logical, cancelled);
        if (!lease.tunneled()) {
            lease.close();
            return Map.of("disposition", "DECLINED");
        }
        var result = new LinkedHashMap<String,Object>();
        result.put("disposition", "READY"); result.put("address", lease.address());
        result.put("lease", lease); result.put("filtered", lease.filtered());
        result.put("connected", lease.connected());
        result.put("streamOrder", order);
        if (lease.stats() != null) result.put("stats", lease.stats());
        return Map.copyOf(result);
    }

    public static InetSocketAddress endpoint(Object value, String name) {
        if (!(value instanceof InetSocketAddress address) || address.getHostString().isBlank()
                || address.getPort() < 1 || address.getPort() > 65535) {
            throw new IllegalArgumentException("Invalid native " + name);
        }
        return address;
    }

    public static void checkCancelled(BooleanSupplier token) {
        if (token.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("MiguelNetwork connection cancelled");
        }
    }

    public static void closeQuietly(AutoCloseable resource) {
        if (resource == null) return;
        try { resource.close(); }
        catch (Exception failure) { MiguelNetwork.LOGGER.warn("Cannot close native interoperability resource", failure); }
    }
}
