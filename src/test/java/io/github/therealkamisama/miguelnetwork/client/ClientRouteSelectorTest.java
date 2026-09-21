package io.github.therealkamisama.miguelnetwork.client;

import io.github.therealkamisama.miguelnetwork.core.TransportProtocol;
import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryFilter;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryManifest;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryRoute;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClientRouteSelectorTest {
    @Test
    void unsignedPlainWsRemainsTheDefault() {
        var plain = route("plain", TransportProtocol.WS, List.of());
        assertEquals(plain, ClientRouteSelector.select(manifest(plain), false).orElseThrow());
    }

    @Test
    void requiredUnsupportedFilterCannotFallThroughToItsRawTarget() {
        var filtered = route("filtered", TransportProtocol.WS,
                List.of(new DiscoveryFilter("unknown:compression", 1, true)));
        assertTrue(ClientRouteSelector.select(manifest(filtered), false).isEmpty());
        var plain = route("advertised-plain", TransportProtocol.WS, List.of());
        assertEquals(plain, ClientRouteSelector.select(manifest(filtered, plain), false).orElseThrow());
    }

    @Test
    void optionalUnknownFilterDoesNotRequireAnAdapter() {
        var optional = route("optional", TransportProtocol.WS,
                List.of(new DiscoveryFilter("unknown:hint", 1, false)));
        assertEquals(optional, ClientRouteSelector.select(manifest(optional), false).orElseThrow());
    }

    @Test
    void operatorSelectedWssRequirementStillApplies() {
        var plain = route("plain", TransportProtocol.WS, List.of());
        var tls = route("tls", TransportProtocol.WSS, List.of());
        assertTrue(ClientRouteSelector.select(manifest(plain), true).isEmpty());
        assertEquals(tls, ClientRouteSelector.select(manifest(plain, tls), true).orElseThrow());
    }

    private static DiscoveryRoute route(String id, TransportProtocol transport, List<DiscoveryFilter> filters) {
        return new DiscoveryRoute(id, transport, "example.test", 35548,
                "miguelnetwork-v1", "127.0.0.1", 25565, 100, filters);
    }

    private static DiscoveryManifest manifest(DiscoveryRoute... routes) {
        return new DiscoveryManifest(MiguelNetworkProtocol.DISCOVERY_PROTOCOL, "unsigned", "example.test:35548",
                "test-nonce", 1, 10, 130, List.of(routes), "unsigned");
    }
}
