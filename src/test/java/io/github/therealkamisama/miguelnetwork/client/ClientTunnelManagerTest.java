package io.github.therealkamisama.miguelnetwork.client;

import com.sun.net.httpserver.HttpServer;
import io.github.therealkamisama.miguelnetwork.config.ClientConfig;
import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ClientTunnelManagerTest {
    @TempDir Path directory;

    @Test
    void unavailableDiscoveryDoesNotReturnADirectEndpointWhenFallbackIsDisabled() throws Exception {
        checkRejected(404, new byte[0], false, "legacyFallback is disabled");
    }

    @Test
    void malformedManifestCannotEnterLegacyProbeEvenWhenEnabled() throws Exception {
        checkRejected(200, "not a manifest".getBytes(java.nio.charset.StandardCharsets.UTF_8), true,
                "rejected untrusted Discovery");
    }

    @Test
    void oversizedManifestCannotEnterLegacyProbeEvenWhenEnabled() throws Exception {
        checkRejected(200, new byte[65 * 1024], true, "rejected untrusted Discovery");
    }

    private void checkRejected(int status, byte[] body, boolean fallback, String message) throws Exception {
        Path config = directory.resolve("client.toml");
        Files.writeString(config, "legacyFallback = " + fallback + "\n");
        ClientConfig.SPEC.load(config);
        ClientTunnelManager.initialize(directory);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(MiguelNetworkProtocol.DISCOVERY_PATH, exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var failure = assertThrows(IllegalStateException.class,
                    () -> ClientTunnelManager.redirect(server.getAddress()));
            assertTrue(failure.getMessage().contains(message), failure.getMessage());
        } finally {
            server.stop(0);
            ClientTunnelManager.stop();
            ClientConfig.SPEC.load(directory.resolve("defaults.toml"));
        }
    }
}
