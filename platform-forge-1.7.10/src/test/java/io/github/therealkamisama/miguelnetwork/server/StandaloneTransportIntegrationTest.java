package io.github.therealkamisama.miguelnetwork.server;

import io.github.therealkamisama.miguelnetwork.client.ClientTunnelManager;
import io.github.therealkamisama.miguelnetwork.core.NativeWstunnel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class StandaloneTransportIntegrationTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void plainWsTransfersWithDiscoveryOrExplicitLegacyFallback(boolean discovery) throws Exception {
        Path binary = NativeWstunnel.resolve(directory);
        int publicPort;
        try (ServerSocket candidate = new ServerSocket(0)) { publicPort = candidate.getLocalPort(); }
        Map<String, String> settings = Map.of(
                "miguelnetwork.wstunnel.path", binary.toString(),
                "miguelnetwork.bindHost", "127.0.0.1",
                "miguelnetwork.public.port", Integer.toString(publicPort),
                "miguelnetwork.discovery.enabled", Boolean.toString(discovery),
                "miguelnetwork.client.legacyFallback", Boolean.toString(!discovery));
        Map<String, String> previous = new java.util.HashMap<>();
        settings.forEach((key, value) -> previous.put(key, System.setProperty(key, value)));
        try (ServerSocket echo = new ServerSocket(0);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            previous.put("miguelnetwork.target.port", System.setProperty("miguelnetwork.target.port",
                    Integer.toString(echo.getLocalPort())));
            executor.submit(() -> {
                while (!echo.isClosed()) {
                    try {
                        Socket socket = echo.accept();
                        executor.submit(() -> {
                            try (socket) { socket.getInputStream().transferTo(socket.getOutputStream()); }
                            catch (Exception ignored) { }
                        });
                    } catch (Exception ignored) { break; }
                }
            });
            try {
                ServerTunnelController.start(directory, echo.getLocalPort());
                InetSocketAddress endpoint = new InetSocketAddress("127.0.0.1", publicPort);
                try (var gameplay = ClientTunnelManager.acquire(endpoint)) {
                    assertNotEquals(endpoint, gameplay.address());
                    try (Socket socket = new Socket()) {
                        socket.connect(gameplay.address(), 3000);
                        socket.setSoTimeout(5000);
                        try (var ping = ClientTunnelManager.acquire(endpoint);
                             Socket pingSocket = new Socket()) {
                            assertEquals(gameplay.address(), ping.address());
                            pingSocket.connect(ping.address(), 3000);
                            pingSocket.setSoTimeout(5000);
                            exchange(pingSocket, new byte[] {5, 6, 7, 8});
                            exchange(socket, new byte[] {1, 2, 3, 4});
                        }
                        // Releasing a concurrent ping must leave the gameplay lease usable.
                        byte[] payload = new byte[64 * 1024];
                        new java.util.Random(42).nextBytes(payload);
                        exchange(socket, payload);
                    }
                }
                try (var reconnect = ClientTunnelManager.acquire(endpoint);
                     Socket socket = new Socket()) {
                    socket.connect(reconnect.address(), 3000);
                    socket.setSoTimeout(5000);
                    exchange(socket, new byte[] {9, 8, 7});
                }
                ServerTunnelController.stop();
                try (ServerSocket rebound = new ServerSocket()) {
                    rebound.setReuseAddress(true);
                    rebound.bind(endpoint);
                }
                assertTrue(ProcessHandle.current().descendants()
                        .noneMatch(p -> p.info().command().orElse("").equalsIgnoreCase(binary.toString())));
            } finally {
                ClientTunnelManager.stop();
                ServerTunnelController.stop();
                echo.close();
                executor.shutdownNow();
            }
        } finally {
            previous.forEach((key, value) -> {
                if (value == null) System.clearProperty(key); else System.setProperty(key, value);
            });
        }
    }

    private static void exchange(Socket socket, byte[] payload) throws Exception {
        socket.getOutputStream().write(payload);
        socket.getOutputStream().flush();
        assertArrayEquals(payload, socket.getInputStream().readNBytes(payload.length));
    }
}
