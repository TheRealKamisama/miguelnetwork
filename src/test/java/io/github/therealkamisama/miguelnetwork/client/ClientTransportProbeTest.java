package io.github.therealkamisama.miguelnetwork.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ClientTransportProbeTest {
    @Test
    void createsWstunnelCompatibleTcpProbeToken() throws Exception {
        String token = ClientTransportProbe.createProbeJwt(25566);
        String[] parts = token.split("\\.");

        assertTrue(parts.length == 3);
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertTrue(payload.contains("\"p\":{\"Tcp\":{\"proxy_protocol\":false}}"));
        assertTrue(payload.contains("\"r\":\"127.0.0.1\""));
        assertTrue(payload.contains("\"rp\":25566"));
    }

    @Test
    void acceptsOnlyACompleteWstunnelWebsocketUpgrade() throws Exception {
        String key = "dGhlIHNhbXBsZSBub25jZQ==";
        String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest(
                (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)
        ));
        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n"
                + "Sec-WebSocket-Protocol: v1\r\n\r\n";

        assertTrue(ClientTransportProbe.isWstunnelUpgrade(response, key));
        assertFalse(ClientTransportProbe.isWstunnelUpgrade(response.replace("Protocol: v1", "Protocol: other"), key));
        assertFalse(ClientTransportProbe.isWstunnelUpgrade(response.replace("101", "200"), key));
    }

    @Test
    void detectsConfiguredRealEndpoint() {
        String endpoint = System.getenv("MIGUELNETWORK_TEST_ENDPOINT");
        assumeTrue(endpoint != null && !endpoint.isBlank());
        String expected = System.getenv().getOrDefault("MIGUELNETWORK_TEST_EXPECTED", "WSS");
        int separator = endpoint.lastIndexOf(':');
        String host = endpoint.substring(0, separator);
        int port = Integer.parseInt(endpoint.substring(separator + 1));

        assertEquals(
                expected,
                ClientTransportProbe.detect(host, port, 25566, "miguelnetwork-v1", Duration.ofSeconds(5))
                        .orElseThrow()
                        .name()
        );
    }

    @Test
    void fallsBackWhenEndpointIsNotWstunnel() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 2, InetAddress.getLoopbackAddress())) {
            Thread rejector = new Thread(() -> {
                for (int attempt = 0; attempt < 2; attempt++) {
                    try (var ignored = server.accept()) {
                        // Reject both the TLS and plain HTTP Upgrade probes.
                    } catch (Exception ignored) {
                        return;
                    }
                }
            });
            rejector.setDaemon(true);
            rejector.start();

            assertTrue(ClientTransportProbe.detect(
                    "127.0.0.1",
                    server.getLocalPort(),
                    25566,
                    "miguelnetwork-v1",
                    Duration.ofSeconds(1)
            ).isEmpty());
            rejector.join(2000);
            assertFalse(rejector.isAlive());
        }
    }
}
