package io.github.therealkamisama.miguelnetwork.server;

import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class GatewayLifecycleTest {
    private static final byte[] HEADER = "GET /miguelnetwork-v1/events HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    @Test void concurrentHalfClosesPreserveReverseTraffic() throws Exception {
        try (ServerSocket upstream = new ServerSocket(0);
             var workers = Executors.newVirtualThreadPerTaskExecutor();
             var gateway = StandaloneGateway.start("127.0.0.1", 0, "127.0.0.1", upstream.getLocalPort(), null)) {
            Future<?> acceptor = workers.submit(() -> {
                var handlers = new ArrayList<Future<?>>();
                for (int i = 0; i < 8; i++) {
                    Socket accepted;
                    try { accepted = upstream.accept(); }
                    catch (Exception e) { throw new RuntimeException(e); }
                    handlers.add(workers.submit(() -> {
                        try (accepted) {
                            accepted.setSoTimeout(5000);
                            byte[] input = accepted.getInputStream().readAllBytes();
                            assertTrue(input.length > HEADER.length);
                            accepted.getOutputStream().write(input);
                        } catch (Exception e) { throw new RuntimeException(e); }
                    }));
                }
                for (var handler : handlers) {
                    try { handler.get(8, TimeUnit.SECONDS); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }
            });
            var clients = new ArrayList<Future<?>>();
            for (int i = 0; i < 8; i++) {
                final byte marker = (byte) i;
                clients.add(workers.submit(() -> {
                    try (Socket socket = new Socket("127.0.0.1", gateway.localPort())) {
                        socket.setSoTimeout(5000);
                        byte[] request = java.util.Arrays.copyOf(HEADER, HEADER.length + 32768);
                        java.util.Arrays.fill(request, HEADER.length, request.length, marker);
                        socket.getOutputStream().write(request);
                        socket.shutdownOutput();
                        assertArrayEquals(request, socket.getInputStream().readAllBytes());
                    } catch (Exception e) { throw new RuntimeException(e); }
                }));
            }
            for (var client : clients) client.get(10, TimeUnit.SECONDS);
            acceptor.get(10, TimeUnit.SECONDS);
        }
    }

    @Test void portConflictLeavesExistingListenerAlone() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            assertThrows(java.net.BindException.class, () ->
                    StandaloneGateway.start("127.0.0.1", occupied.getLocalPort(), "127.0.0.1", 9, null));
            assertFalse(occupied.isClosed());
        }
    }

    @Test void upstreamResetClosesTheClientInsteadOfLeavingAForwardPumpBlocked() throws Exception {
        try (ServerSocket upstream = new ServerSocket(0);
             var worker = Executors.newVirtualThreadPerTaskExecutor();
             var gateway = StandaloneGateway.start("127.0.0.1", 0, "127.0.0.1", upstream.getLocalPort(), null);
             Socket client = new Socket("127.0.0.1", gateway.localPort())) {
            Future<?> reset = worker.submit(() -> {
                try (Socket socket = upstream.accept()) {
                    socket.getInputStream().readNBytes(HEADER.length);
                    socket.setSoLinger(true, 0);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            client.setSoTimeout(3000);
            client.getOutputStream().write(HEADER);
            try { assertEquals(-1, client.getInputStream().read()); }
            catch (java.net.SocketTimeoutException e) { fail("Gateway retained a reset upstream", e); }
            catch (java.net.SocketException expectedReset) { }
            reset.get(5, TimeUnit.SECONDS);
        }
    }
}
