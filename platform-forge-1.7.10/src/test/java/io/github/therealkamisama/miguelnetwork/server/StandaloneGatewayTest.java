package io.github.therealkamisama.miguelnetwork.server;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandaloneGatewayTest {
    @Test
    void servesDiscoveryOnThePublicGatewayPort() throws Exception {
        byte[] responseBody = "{\"mode\":\"unsigned\"}".getBytes(StandardCharsets.UTF_8);
        try (StandaloneGateway gateway = StandaloneGateway.start(
                "127.0.0.1", 0, "127.0.0.1", 9,
                (audience, request) -> {
                    assertEquals("play.example.test:35548", audience);
                    assertEquals("request", new String(request, StandardCharsets.UTF_8));
                    return responseBody;
                })) {
            String request = "POST /.well-known/miguelnetwork/v1 HTTP/1.1\r\n"
                    + "Host: play.example.test:35548\r\n"
                    + "Content-Length: 7\r\nConnection: close\r\n\r\nrequest";
            String response = exchange(gateway.localPort(), request);

            assertTrue(response.startsWith("HTTP/1.1 200 OK\r\n"));
            assertTrue(response.endsWith(new String(responseBody, StandardCharsets.UTF_8)));
        }
    }

    @Test
    void forwardsWstunnelUpgradeTrafficWithoutReencodingIt() throws Exception {
        try (ServerSocket upstream = new ServerSocket(0)) {
            CompletableFuture<String> received = new CompletableFuture<>();
            Thread.ofVirtual().start(() -> {
                try (Socket socket = upstream.accept()) {
                    received.complete(readHeader(socket));
                    socket.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\n"
                            + "Upgrade: websocket\r\nConnection: upgrade\r\n\r\n")
                            .getBytes(StandardCharsets.ISO_8859_1));
                } catch (Exception exception) {
                    received.completeExceptionally(exception);
                }
            });
            try (StandaloneGateway gateway = StandaloneGateway.start(
                    "127.0.0.1", 0, "127.0.0.1", upstream.getLocalPort(), null)) {
                String upgrade = "GET /miguelnetwork-v1/events HTTP/1.1\r\n"
                        + "Host: play.example.test:35548\r\n"
                        + "Upgrade: websocket\r\nConnection: Upgrade\r\n\r\n";
                String response = exchange(gateway.localPort(), upgrade);

                assertEquals(upgrade, received.orTimeout(Duration.ofSeconds(3).toMillis(),
                        java.util.concurrent.TimeUnit.MILLISECONDS).join());
                assertTrue(response.startsWith("HTTP/1.1 101 Switching Protocols\r\n"));
            }
        }
    }

    private static String exchange(int port, String request) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
            socket.shutdownOutput();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    private static String readHeader(Socket socket) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int value = socket.getInputStream().read();
            if (value < 0) {
                break;
            }
            output.write(value);
            matched = switch (matched) {
                case 0 -> value == '\r' ? 1 : 0;
                case 1 -> value == '\n' ? 2 : 0;
                case 2 -> value == '\r' ? 3 : 0;
                case 3 -> value == '\n' ? 4 : 0;
                default -> 4;
            };
        }
        return output.toString(StandardCharsets.ISO_8859_1);
    }
}
