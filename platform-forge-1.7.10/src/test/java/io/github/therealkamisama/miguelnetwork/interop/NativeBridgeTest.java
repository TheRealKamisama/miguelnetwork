package io.github.therealkamisama.miguelnetwork.interop;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeBridgeTest {
    private Map<String,Object> descriptor(int api, String game, Object streamVersion) {
        return Map.of("apiMajor", api, "minecraftVersion", game, "filters", Map.of("zstdnet-stream", streamVersion),
                "streamLayer", StreamOrder.layer(StreamOrder.ZSTD),
                "agreeStreamOrder", (Function<Map<String,Object>,Map<String,Object>>) request ->
                        StreamOrder.agree(StreamOrder.ZSTD, request),
                "openStream", (Function<Map<String,Object>,Map<String,Object>>) ignored -> Map.of(),
                "ownsEndpoint", (Predicate<InetSocketAddress>) ignored -> false);
    }

    @Test void compatibilityRequiresTheProtocolAndExactGamePlatform() {
        assertNull(NativeBridge.negotiate(2));
        assertNull(ZstdPeer.fromDescriptor(descriptor(2, NativeBridge.MINECRAFT_VERSION, 1)));
        assertNull(ZstdPeer.fromDescriptor(descriptor(1, "other-game", 1)));
        assertTrue(ZstdPeer.fromDescriptor(descriptor(1, NativeBridge.MINECRAFT_VERSION, 1)).supportsStream());
        assertFalse(ZstdPeer.fromDescriptor(descriptor(1, NativeBridge.MINECRAFT_VERSION, 2)).supportsStream());
        assertFalse(ZstdPeer.fromDescriptor(descriptor(1, NativeBridge.MINECRAFT_VERSION, "1")).supportsStream());
    }

    @Test void invalidStreamResponsesCloseTheirPartialLeaseAndNeverReturnATarget() {
        AtomicBoolean closed = new AtomicBoolean();
        var peer = new ZstdPeer(Map.of("filters", Map.of("zstdnet-stream", 1),
                "streamLayer", StreamOrder.layer(StreamOrder.ZSTD),
                "agreeStreamOrder", (Function<Map<String,Object>,Map<String,Object>>) request ->
                        StreamOrder.agree(StreamOrder.ZSTD, request),
                "ownsEndpoint", (Predicate<InetSocketAddress>) ignored -> false,
                "openStream", (Function<Map<String,Object>,Map<String,Object>>) ignored ->
                        Map.of("address", new InetSocketAddress("192.0.2.1", 35565),
                                "streamOrder", StreamOrder.require(StreamOrder.agree(StreamOrder.ZSTD,
                                        Map.of("peerLayer", StreamOrder.layer(StreamOrder.TRANSPORT)))),
                                "lease", (AutoCloseable) () -> closed.set(true),
                                "stats", (Supplier<Map<String,Object>>) Map::of)));
        assertThrows(IllegalStateException.class, () -> peer.open(new InetSocketAddress("127.0.0.1", 12345),
                InetSocketAddress.createUnresolved("logical.example.test", 25565)));
        assertTrue(closed.get());
    }

    @Test void serverStateUsesEffectiveEndpointsAndRejectsMalformedReadiness() {
        var listener = InetSocketAddress.createUnresolved("::1", 35565);
        var backend = InetSocketAddress.createUnresolved("127.0.0.1", 25566);
        var ready = ZstdPeer.ServerState.decode(Map.of("generation", 1L, "ready", true,
                "listener", listener, "backend", backend));
        assertEquals(listener, ready.listener()); assertEquals(backend, ready.backend());
        var stopped = ZstdPeer.ServerState.decode(Map.of("generation", 2L, "ready", false, "backend", backend));
        assertFalse(stopped.ready()); assertEquals(backend, stopped.backend());
        assertThrows(IllegalStateException.class, () ->
                ZstdPeer.ServerState.decode(Map.of("generation", 3L, "ready", true)));
        assertThrows(IllegalStateException.class, () ->
                ZstdPeer.ServerState.decode(Map.of("generation", 3, "ready", false)));
    }

    @SuppressWarnings("unchecked")
    @Test void cancelledPreparationFailsBeforeAnyRouteIsSelected() {
        var prepare = (Function<Map<String,Object>,Map<String,Object>>) NativeBridge.negotiate(1).get("prepareConnection");
        assertThrows(java.util.concurrent.CancellationException.class, () ->
                prepare.apply(Map.of("logical", InetSocketAddress.createUnresolved("logical.example.test", 25565),
                        "cancelled", (java.util.function.BooleanSupplier) () -> true)));
        assertThrows(IllegalArgumentException.class, () -> prepare.apply(Map.of("logical", "invalid")));
    }
}
