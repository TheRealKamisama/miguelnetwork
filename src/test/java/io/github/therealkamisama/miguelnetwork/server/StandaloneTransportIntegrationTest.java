package io.github.therealkamisama.miguelnetwork.server;

import io.github.therealkamisama.miguelnetwork.client.ClientTunnelManager;
import io.github.therealkamisama.miguelnetwork.config.ClientConfig;
import io.github.therealkamisama.miguelnetwork.config.ServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real bundled sidecars and gateway, without starting Minecraft or exposing a public listener. */
class StandaloneTransportIntegrationTest {
    @TempDir Path directory;

    @Test
    void unsignedDiscoveryCarriesBytesAndReconnectsAfterSidecarRestart() throws Exception {
        Path serverDirectory = Files.createDirectory(directory.resolve("server"));
        Path clientDirectory = Files.createDirectory(directory.resolve("client"));
        Files.writeString(serverDirectory.resolve("server.properties"), "server-ip=127.0.0.1\n");
        Path clientConfig = clientDirectory.resolve("client.toml");
        Files.writeString(clientConfig, "legacyFallback = false\n");
        ClientConfig.SPEC.load(clientConfig);
        int gatewayPort;
        try (ServerSocket candidate = listener()) { gatewayPort = candidate.getLocalPort(); }
        Path serverConfig = serverDirectory.resolve("server.toml");
        Files.writeString(serverConfig, "bindHost = \"127.0.0.1\"\npublicPort = " + gatewayPort + "\n");
        ServerConfig.SPEC.load(serverConfig);
        byte[] payload = new byte[128 * 1024];
        new Random(1122).nextBytes(payload);

        try (ServerSocket backend = listener(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            backend.setSoTimeout(15_000);
            for (int cycle = 0; cycle < 2; cycle++) {
                ServerTunnelController.start(serverDirectory, backend.getLocalPort());
                ClientTunnelManager.initialize(clientDirectory);
                var destination = new InetSocketAddress("127.0.0.1", gatewayPort);
                var redirected = ClientTunnelManager.redirect(destination);
                assertNotEquals(destination, redirected, "Discovery must select a tunnel with fallback disabled");
                assertEquals(redirected, ClientTunnelManager.redirect(destination), "Reuse the live sidecar");
                var echoed = executor.submit(() -> {
                    try (Socket peer = backend.accept()) {
                        peer.setSoTimeout(10_000);
                        byte[] received = peer.getInputStream().readNBytes(payload.length);
                        peer.getOutputStream().write(received);
                        return received;
                    }
                });
                try (Socket client = new Socket()) {
                    client.connect(redirected, 5_000);
                    client.setSoTimeout(10_000);
                    client.getOutputStream().write(payload);
                    assertArrayEquals(payload, client.getInputStream().readNBytes(payload.length));
                }
                assertArrayEquals(payload, echoed.get(15, TimeUnit.SECONDS));
                ClientTunnelManager.stop();
                ServerTunnelController.stop();
                assertListenerClosed(redirected);
                assertListenerClosed(destination);
            }
        } finally {
            ClientTunnelManager.stop();
            ServerTunnelController.stop();
            ClientConfig.SPEC.load(directory.resolve("client-defaults.toml"));
            ServerConfig.SPEC.load(directory.resolve("server-defaults.toml"));
        }
    }

    private static ServerSocket listener() throws Exception {
        ServerSocket socket = new ServerSocket();
        socket.bind(new InetSocketAddress("127.0.0.1", 0));
        return socket;
    }

    private static void assertListenerClosed(InetSocketAddress endpoint) {
        assertThrows(java.io.IOException.class, () -> {
            try (Socket probe = new Socket()) { probe.connect(endpoint, 1_000); }
        }, "Listener remains open after shutdown: " + endpoint);
    }
}
