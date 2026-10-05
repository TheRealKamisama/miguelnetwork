package io.github.therealkamisama.miguelnetwork.client;

import com.sun.net.httpserver.HttpServer;
import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import io.github.therealkamisama.miguelnetwork.core.TransportProtocol;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryCodec;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryFilter;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryManifest;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryRoute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ClientRoutingPolicyTest {
    @AfterEach void cleanup() { ClientTunnelManager.stop(); }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", MiguelNetworkProtocol.ZSTDNET_FILTER})
    void requiredFiltersNeverEnterLegacyFallback(String filter) throws Exception {
        try (Endpoint endpoint = new Endpoint(filter, null, 200)) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> ClientTunnelManager.acquire(endpoint.address()));
            assertTrue(error.getMessage().contains("no compatible route"));
            endpoint.assertHealthy();
        }
    }

    @Test void malformedManifestNeverEntersLegacyFallback() throws Exception {
        try (Endpoint endpoint = new Endpoint(null, "{}".getBytes(), 200)) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> ClientTunnelManager.acquire(endpoint.address()));
            assertTrue(error.getMessage().contains("rejected untrusted Discovery"));
            endpoint.assertHealthy();
        }
    }

    @Test void oversizedManifestNeverEntersLegacyFallback() throws Exception {
        try (Endpoint endpoint = new Endpoint(null, new byte[DiscoveryCodec.MAX_DOCUMENT_BYTES + 1], 200)) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> ClientTunnelManager.acquire(endpoint.address()));
            assertTrue(error.getMessage().contains("rejected untrusted Discovery"));
            endpoint.assertHealthy();
        }
    }

    @Test void unavailableDiscoveryFailsWhenFallbackDisabled() throws Exception {
        String key = "miguelnetwork.client.legacyFallback";
        String previous = System.setProperty(key, "false");
        try (Endpoint endpoint = new Endpoint(null, new byte[0], 404)) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> ClientTunnelManager.acquire(endpoint.address()));
            assertTrue(error.getMessage().contains("legacyFallback is disabled"));
            endpoint.assertHealthy();
        } finally {
            if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
        }
    }

    private static final class Endpoint implements AutoCloseable {
        private final HttpServer server;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        Endpoint(String filter, byte[] fixedResponse, int status) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(MiguelNetworkProtocol.DISCOVERY_PATH, exchange -> {
                try {
                    byte[] response = fixedResponse;
                    if (response == null) {
                        String nonce = DiscoveryCodec.decodeRequestNonce(exchange.getRequestBody().readAllBytes());
                        String audience = exchange.getRequestHeaders().getFirst("Host");
                        long now = Instant.now().getEpochSecond();
                        response = DiscoveryCodec.encodeUnsigned(new DiscoveryManifest(
                                MiguelNetworkProtocol.DISCOVERY_PROTOCOL, "unsigned", audience, nonce, 1,
                                now, now + 60, List.of(new DiscoveryRoute("filtered", TransportProtocol.WS,
                                "127.0.0.1", server.getAddress().getPort(), "miguelnetwork-v1", "127.0.0.1",
                                25565, 100, List.of(new DiscoveryFilter(filter, 1, true)))), "unsigned"));
                    }
                    exchange.sendResponseHeaders(status, response.length == 0 ? -1 : response.length);
                    exchange.getResponseBody().write(response);
                } catch (Throwable error) { failure.set(error); }
                finally { exchange.close(); }
            });
            server.start();
        }

        InetSocketAddress address() { return server.getAddress(); }
        void assertHealthy() { assertNull(failure.get()); }
        @Override public void close() { server.stop(0); }
    }
}
