package io.github.therealkamisama.miguelnetwork.server;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandaloneGatewayTest {
    private static final String UPGRADE = "GET /miguelnetwork-v1/events HTTP/1.1\r\n"
            + "Host: play.example.test\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n";
    private static final String SWITCHING = "HTTP/1.1 101 Switching Protocols\r\n"
            + "Upgrade: websocket\r\nConnection: Upgrade\r\n\r\n";

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
            Thread responder = new Thread(() -> {
                try (Socket socket = upstream.accept()) {
                    received.complete(readHeader(socket));
                    socket.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\n"
                            + "Upgrade: websocket\r\nConnection: upgrade\r\n\r\n")
                            .getBytes(StandardCharsets.ISO_8859_1));
                } catch (Exception exception) {
                    received.completeExceptionally(exception);
                }
            });
            responder.setDaemon(true);
            responder.start();
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

    @Test
    void relaysConcurrentConnectionsAndPreservesHalfClose() throws Exception {
        var workers = Executors.newCachedThreadPool();
        try (ServerSocket upstream = new ServerSocket(0);
             StandaloneGateway gateway = StandaloneGateway.start(
                     "127.0.0.1", 0, "127.0.0.1", upstream.getLocalPort(), null)) {
            upstream.setSoTimeout(3_000);
            CountDownLatch bothConnected = new CountDownLatch(2);
            List<Future<?>> backends = new ArrayList<>();
            List<Future<?>> clients = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                backends.add(workers.submit(() -> {
                    try (Socket socket = upstream.accept()) {
                        socket.setSoTimeout(3_000);
                        assertEquals(UPGRADE, readHeader(socket));
                        bothConnected.countDown();
                        assertTrue(bothConnected.await(3, TimeUnit.SECONDS));
                        socket.getOutputStream().write((SWITCHING + "ready").getBytes(StandardCharsets.US_ASCII));
                        byte[] request = socket.getInputStream().readAllBytes();
                        // The reply must still reach the client after it half-closes.
                        socket.getOutputStream().write(request);
                    }
                    return null;
                }));
                String payload = "payload-" + index;
                clients.add(workers.submit(() -> {
                    try (Socket socket = new Socket("127.0.0.1", gateway.localPort())) {
                        socket.setSoTimeout(3_000);
                        socket.getOutputStream().write(UPGRADE.getBytes(StandardCharsets.US_ASCII));
                        assertEquals(SWITCHING, readHeader(socket));
                        assertEquals("ready", new String(socket.getInputStream().readNBytes(5), StandardCharsets.US_ASCII));
                        socket.getOutputStream().write(payload.getBytes(StandardCharsets.US_ASCII));
                        socket.shutdownOutput();
                        assertEquals(payload, new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII));
                    }
                    return null;
                }));
            }
            for (Future<?> future : clients) {
                future.get(5, TimeUnit.SECONDS);
            }
            for (Future<?> future : backends) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void closingGatewayDisconnectsAnActiveTunnelInBothDirections() throws Exception {
        try (ServerSocket upstream = new ServerSocket(0);
             StandaloneGateway gateway = StandaloneGateway.start(
                     "127.0.0.1", 0, "127.0.0.1", upstream.getLocalPort(), null);
             Socket client = new Socket("127.0.0.1", gateway.localPort())) {
            upstream.setSoTimeout(3_000);
            client.setSoTimeout(3_000);
            client.getOutputStream().write(UPGRADE.getBytes(StandardCharsets.US_ASCII));
            try (Socket backend = upstream.accept()) {
                backend.setSoTimeout(3_000);
                assertEquals(UPGRADE, readHeader(backend));
                backend.getOutputStream().write(SWITCHING.getBytes(StandardCharsets.US_ASCII));
                assertEquals(SWITCHING, readHeader(client));
                gateway.close();
                assertEquals(-1, client.getInputStream().read());
                assertEquals(-1, backend.getInputStream().read());
            }
        }
    }

    @Test
    void refusesExcessConnectionsAndRecoversAfterSlotsAreReleased() throws Exception {
        CountDownLatch occupied = new CountDownLatch(128);
        CountDownLatch release = new CountDownLatch(1);
        List<Socket> clients = new ArrayList<>();
        String request = "POST /.well-known/miguelnetwork/v1 HTTP/1.1\r\n"
                + "Host: play.example.test\r\nContent-Length: 0\r\n\r\n";
        try (StandaloneGateway gateway = StandaloneGateway.start(
                "127.0.0.1", 0, "127.0.0.1", 9, (audience, body) -> {
                    occupied.countDown();
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                    return "ok".getBytes(StandardCharsets.US_ASCII);
                })) {
            try {
                for (int index = 0; index < 128; index++) {
                    Socket client = new Socket("127.0.0.1", gateway.localPort());
                    clients.add(client);
                    client.setSoTimeout(5_000);
                    client.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                }
                assertTrue(occupied.await(5, TimeUnit.SECONDS));
                try (Socket excess = new Socket("127.0.0.1", gateway.localPort())) {
                    excess.setSoTimeout(3_000);
                    try {
                        excess.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                        assertEquals(-1, excess.getInputStream().read());
                    } catch (SocketException expected) {
                        // A close with unread request data can be reported as a reset.
                    }
                }
                release.countDown();
                for (Socket client : clients) {
                    assertTrue(new String(client.getInputStream().readAllBytes(), StandardCharsets.US_ASCII)
                            .startsWith("HTTP/1.1 200 OK\r\n"));
                }
                assertTrue(exchange(gateway.localPort(), request).endsWith("ok"));
            } finally {
                release.countDown();
                for (Socket client : clients) {
                    client.close();
                }
            }
        }
    }

    private static String exchange(int port, String request) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
            socket.setSoTimeout(3_000);
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
