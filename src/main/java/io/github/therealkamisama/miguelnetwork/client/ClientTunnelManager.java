package io.github.therealkamisama.miguelnetwork.client;

import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import io.github.therealkamisama.miguelnetwork.config.ClientConfig;
import io.github.therealkamisama.miguelnetwork.core.ManagedWstunnelProcess;
import io.github.therealkamisama.miguelnetwork.core.NativeWstunnel;
import io.github.therealkamisama.miguelnetwork.core.WstunnelCommands;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;

public final class ClientTunnelManager {
    private static ManagedWstunnelProcess process;
    private static String currentEndpoint;
    private static int currentLocalPort;

    private ClientTunnelManager() {
    }

    public static synchronized InetSocketAddress redirect(InetSocketAddress original) {
        if (!ClientConfig.enabled() || !ClientConfig.allows(original.getHostString(), original.getPort())) {
            return original;
        }

        String host = original.getHostString();
        int publicPort = original.getPort();
        String endpoint = host + ":" + publicPort;
        if (process != null && process.isAlive() && endpoint.equals(currentEndpoint)) {
            return new InetSocketAddress("127.0.0.1", currentLocalPort);
        }

        stop();
        try {
            int localPort = findCandidatePort();
            int targetPort = ClientConfig.targetPort();
            String pathPrefix = ClientConfig.pathPrefix();
            boolean verifyCertificate = ClientConfig.verifyCertificate();
            if (!verifyCertificate) {
                MiguelNetwork.LOGGER.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                MiguelNetwork.LOGGER.warn("MiguelNetwork TLS CERTIFICATE VERIFICATION IS DISABLED (DEVELOPMENT ONLY)");
                MiguelNetwork.LOGGER.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            }
            Path executable = NativeWstunnel.resolve(FMLPaths.GAMEDIR.get());
            process = ManagedWstunnelProcess.start(
                    WstunnelCommands.client(executable, host, publicPort, localPort, targetPort, pathPrefix,
                            verifyCertificate),
                    line -> line.contains("Starting TCP server listening cnx on"),
                    MiguelNetwork.LOGGER
            );
            process.awaitReady(Duration.ofSeconds(10));
            currentEndpoint = endpoint;
            currentLocalPort = localPort;
            MiguelNetwork.LOGGER.info("MiguelNetwork redirects {} to 127.0.0.1:{}", endpoint, localPort);
            return new InetSocketAddress("127.0.0.1", localPort);
        } catch (IOException exception) {
            stop();
            throw new IllegalStateException("Cannot prepare MiguelNetwork tunnel for " + endpoint, exception);
        }
    }

    private static int findCandidatePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        }
    }

    public static synchronized void stop() {
        if (process != null) {
            process.close();
        }
        process = null;
        currentEndpoint = null;
        currentLocalPort = 0;
    }
}
