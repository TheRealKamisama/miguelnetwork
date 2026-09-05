package io.github.therealkamisama.miguelnetwork.client;

import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import io.github.therealkamisama.miguelnetwork.config.ClientConfig;
import io.github.therealkamisama.miguelnetwork.core.EndpointMatcher;
import io.github.therealkamisama.miguelnetwork.core.ManagedWstunnelProcess;
import io.github.therealkamisama.miguelnetwork.core.NativeWstunnel;
import io.github.therealkamisama.miguelnetwork.core.TransportProtocol;
import io.github.therealkamisama.miguelnetwork.core.WstunnelCommands;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientTunnelManager {
    private static final Map<String, ClientTunnel> TUNNELS = new LinkedHashMap<>(16, 0.75f, true);
    private static boolean insecureWarningLogged;
    private static boolean unencryptedWarningLogged;

    private ClientTunnelManager() {
    }

    public static synchronized InetSocketAddress redirect(InetSocketAddress original) {
        if (!ClientConfig.enabled() || !ClientConfig.allows(original.getHostString(), original.getPort())) {
            return original;
        }

        String host = original.getHostString();
        int publicPort = original.getPort();
        String endpoint = EndpointMatcher.formatEndpoint(host, publicPort);
        ClientTunnel existing = TUNNELS.get(endpoint);
        if (existing != null && existing.process().isAlive()) {
            return existing.localAddress();
        }
        closeAndRemove(endpoint);

        ManagedWstunnelProcess started = null;
        try {
            evictToCapacity(ClientConfig.maxTunnelProcesses());
            int localPort = findCandidatePort();
            int targetPort = ClientConfig.targetPort();
            String pathPrefix = ClientConfig.pathPrefix();
            TransportProtocol transport = ClientConfig.transport();
            boolean verifyCertificate = ClientConfig.verifyCertificate();
            if (!transport.usesTls() && !unencryptedWarningLogged) {
                unencryptedWarningLogged = true;
                MiguelNetwork.LOGGER.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                MiguelNetwork.LOGGER.warn("MiguelNetwork CLIENT TRANSPORT IS UNENCRYPTED WS (DEVELOPMENT ONLY)");
                MiguelNetwork.LOGGER.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            } else if (!verifyCertificate && !insecureWarningLogged) {
                insecureWarningLogged = true;
                MiguelNetwork.LOGGER.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                MiguelNetwork.LOGGER.warn("MiguelNetwork TLS CERTIFICATE VERIFICATION IS DISABLED (DEVELOPMENT ONLY)");
                MiguelNetwork.LOGGER.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            }
            Path executable = NativeWstunnel.resolve(FMLPaths.GAMEDIR.get());
            started = ManagedWstunnelProcess.start(
                    WstunnelCommands.client(executable, host, publicPort, localPort, targetPort, pathPrefix,
                            transport, verifyCertificate),
                    line -> line.contains("Starting TCP server listening cnx on"),
                    MiguelNetwork.LOGGER
            );
            started.awaitReady(Duration.ofSeconds(10));
            ClientTunnel tunnel = new ClientTunnel(started, localPort);
            TUNNELS.put(endpoint, tunnel);
            MiguelNetwork.LOGGER.info("MiguelNetwork redirects {} to 127.0.0.1:{}", endpoint, localPort);
            return tunnel.localAddress();
        } catch (IOException exception) {
            if (started != null) {
                started.close();
            }
            throw new IllegalStateException("Cannot prepare MiguelNetwork tunnel for " + endpoint, exception);
        }
    }

    private static void evictToCapacity(int maximum) {
        while (TUNNELS.size() >= maximum) {
            Iterator<Map.Entry<String, ClientTunnel>> iterator = TUNNELS.entrySet().iterator();
            Map.Entry<String, ClientTunnel> eldest = iterator.next();
            iterator.remove();
            eldest.getValue().process().close();
            MiguelNetwork.LOGGER.info("MiguelNetwork evicted least-recently-used tunnel for {}", eldest.getKey());
        }
    }

    private static void closeAndRemove(String endpoint) {
        ClientTunnel removed = TUNNELS.remove(endpoint);
        if (removed != null) {
            removed.process().close();
        }
    }

    private static int findCandidatePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        }
    }

    public static synchronized void stop() {
        for (ClientTunnel tunnel : TUNNELS.values()) {
            tunnel.process().close();
        }
        TUNNELS.clear();
    }

    private record ClientTunnel(ManagedWstunnelProcess process, int localPort) {
        private InetSocketAddress localAddress() {
            return new InetSocketAddress("127.0.0.1", localPort);
        }
    }
}
