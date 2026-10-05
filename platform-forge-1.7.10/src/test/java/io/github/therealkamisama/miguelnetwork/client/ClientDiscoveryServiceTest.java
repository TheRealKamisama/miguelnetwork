package io.github.therealkamisama.miguelnetwork.client;

import com.sun.net.httpserver.HttpServer;
import io.github.therealkamisama.miguelnetwork.core.EndpointMatcher;
import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import io.github.therealkamisama.miguelnetwork.core.TransportProtocol;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryCodec;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryManifest;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryRoute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientDiscoveryServiceTest {
    @TempDir
    java.nio.file.Path temporaryDirectory;

    @Test
    void fallsBackToHttpAndAcceptsUnsignedDiscoveryByDefault() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
        server.createContext(MiguelNetworkProtocol.DISCOVERY_PATH, exchange -> {
            byte[] request = exchange.getRequestBody().readAllBytes();
            String nonce = DiscoveryCodec.decodeRequestNonce(request);
            String audience = exchange.getRequestHeaders().getFirst("Host").toLowerCase();
            long now = Instant.now().getEpochSecond();
            byte[] response = DiscoveryCodec.encodeUnsigned(new DiscoveryManifest(
                    MiguelNetworkProtocol.DISCOVERY_PROTOCOL, "unsigned", audience, nonce, 1,
                    now, now + 120,
                    List.of(new DiscoveryRoute(
                            "primary", TransportProtocol.WS, "127.0.0.1", server.getAddress().getPort(),
                            "miguelnetwork-v1", "127.0.0.1", 25567, 100, List.of())),
                    "unsigned"));
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            ClientDiscoveryService discovery = new ClientDiscoveryService(
                    ClientTrustStore.open(temporaryDirectory.resolve("trust.json")),
                    Duration.ofMillis(500), false);

            DiscoveryManifest manifest = discovery.discover("127.0.0.1", port, Duration.ofSeconds(1));

            assertEquals(EndpointMatcher.formatEndpoint("127.0.0.1", port), manifest.audience());
            assertEquals(TransportProtocol.WS, manifest.routes().getFirst().transport());
            assertEquals(25567, manifest.routes().getFirst().wstunnelTargetPort());
        } finally {
            server.stop(0);
        }
    }
}
