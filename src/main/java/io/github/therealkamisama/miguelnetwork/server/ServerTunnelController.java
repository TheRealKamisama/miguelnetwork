package io.github.therealkamisama.miguelnetwork.server;

import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import io.github.therealkamisama.miguelnetwork.compat.ZstdNetServerCompatibility;
import io.github.therealkamisama.miguelnetwork.config.ServerConfig;
import io.github.therealkamisama.miguelnetwork.core.ManagedWstunnelProcess;
import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import io.github.therealkamisama.miguelnetwork.core.NativeWstunnel;
import io.github.therealkamisama.miguelnetwork.core.TransportProtocol;
import io.github.therealkamisama.miguelnetwork.core.WstunnelCommands;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public final class ServerTunnelController {
    private static ManagedWstunnelProcess process;
    private static DiscoveryHttpServer discoveryServer;

    private ServerTunnelController() {
    }

    public static synchronized void start(MinecraftServer server) {
        if (!ServerConfig.enabled()) {
            MiguelNetwork.LOGGER.info("MiguelNetwork server tunnel is disabled");
            return;
        }
        stop();
        try {
            int publicPort = ServerConfig.publicPort();
            int targetPort = ServerConfig.targetPort(server.getPort());
            OptionalInt detectedZstdPort = ZstdNetServerCompatibility.discoverListenPort();
            Integer zstdPort = detectedZstdPort.isPresent() ? detectedZstdPort.getAsInt() : null;
            List<Integer> allowedTargetPorts = new ArrayList<>();
            allowedTargetPorts.add(targetPort);
            if (zstdPort != null && zstdPort != targetPort) {
                allowedTargetPorts.add(zstdPort);
            }
            String bindHost = ServerConfig.bindHost();
            String pathPrefix = ServerConfig.pathPrefix();
            TransportProtocol transport = ServerConfig.transport();
            Path gameDirectory = FMLPaths.GAMEDIR.get();
            Path executable = NativeWstunnel.resolve(gameDirectory);
            Path generated = gameDirectory.resolve("config/miguelnetwork/generated/restrictions.yaml");
            Files.createDirectories(generated.getParent());
            Files.write(generated, restrictions(pathPrefix, allowedTargetPorts).getBytes(StandardCharsets.UTF_8));

            Path certificate;
            Path privateKey;
            String certificateValue = ServerConfig.certificate();
            String privateKeyValue = ServerConfig.privateKey();
            if (!transport.usesTls()) {
                certificate = null;
                privateKey = null;
                MiguelNetwork.LOGGER.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                MiguelNetwork.LOGGER.warn("MiguelNetwork SERVER TRANSPORT IS UNENCRYPTED WS");
                MiguelNetwork.LOGGER.warn("Use this only behind a TLS reverse proxy or for an isolated test");
                MiguelNetwork.LOGGER.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            } else if (certificateValue.isEmpty() && privateKeyValue.isEmpty()) {
                boolean allowBuiltInSelfSigned = ServerConfig.allowBuiltInSelfSigned();
                if (!allowBuiltInSelfSigned) {
                    throw new IOException("Missing TLS certificate and private key. For local development only, set "
                            + "-Dmiguelnetwork.tls.allowBuiltInSelfSigned=true");
                }
                certificate = null;
                privateKey = null;
                MiguelNetwork.LOGGER.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                MiguelNetwork.LOGGER.warn("MiguelNetwork IS USING WSTUNNEL'S BUILT-IN SELF-SIGNED CERTIFICATE (DEVELOPMENT ONLY)");
                MiguelNetwork.LOGGER.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            } else if (certificateValue.isEmpty() || privateKeyValue.isEmpty()) {
                throw new IOException("Both -Dmiguelnetwork.tls.certificate and -Dmiguelnetwork.tls.privateKey are required");
            } else {
                certificate = requiredPath("miguelnetwork.tls.certificate", certificateValue);
                privateKey = requiredPath("miguelnetwork.tls.privateKey", privateKeyValue);
            }
            process = ManagedWstunnelProcess.start(
                    WstunnelCommands.server(executable, bindHost, publicPort, generated, transport,
                            certificate, privateKey),
                    line -> line.contains("Starting wstunnel server listening on"),
                    MiguelNetwork.LOGGER
            );
            process.awaitReady(Duration.ofSeconds(10));
            MiguelNetwork.LOGGER.info("MiguelNetwork {} listener is ready on {}:{} -> 127.0.0.1:{}",
                    transport, bindHost, publicPort, targetPort);
            if (ServerConfig.discoveryEnabled()) {
                discoveryServer = DiscoveryHttpServer.start(gameDirectory, targetPort, zstdPort);
                MiguelNetwork.LOGGER.info(
                        "MiguelNetwork Discovery is ready on http://{}:{}{}",
                        ServerConfig.discoveryBindHost(), ServerConfig.discoveryPort(), MiguelNetworkProtocol.DISCOVERY_PATH
                );
            }
        } catch (Exception exception) {
            stop();
            MiguelNetwork.LOGGER.error("MiguelNetwork server tunnel failed to start", exception);
        }
    }

    private static Path requiredPath(String property, String value) throws IOException {
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IOException("Configured file does not exist: " + path);
        }
        return path;
    }

    static String restrictions(String pathPrefix, int targetPort) {
        return restrictions(pathPrefix, List.of(targetPort));
    }

    static String restrictions(String pathPrefix, List<Integer> targetPorts) {
        String escapedPrefix = pathPrefix.replace("\\", "\\\\").replace("\"", "\\\"");
        StringBuilder result = new StringBuilder("restrictions:\n")
                .append("  - name: \"MiguelNetwork Minecraft only\"\n")
                .append("    description: \"Only TCP forwarding to approved loopback listeners\"\n")
                .append("    match:\n")
                .append("      - !PathPrefix \"^").append(escapedPrefix).append("$\"\n")
                .append("    allow:\n")
                .append("      - !Tunnel\n")
                .append("        protocol:\n")
                .append("          - Tcp\n")
                .append("        port:\n");
        for (int targetPort : targetPorts.stream().distinct().toList()) {
            if (targetPort < 1 || targetPort > 65535) {
                throw new IllegalArgumentException("Invalid target port " + targetPort);
            }
            result.append("          - \"").append(targetPort).append("\"\n");
        }
        return result
                .append("        host: \"^$\"\n")
                .append("        cidr:\n")
                .append("          - \"127.0.0.1/32\"\n")
                .append("          - \"::1/128\"\n")
                .toString();
    }

    public static synchronized void stop() {
        if (discoveryServer != null) {
            discoveryServer.close();
            discoveryServer = null;
        }
        if (process != null) {
            process.close();
            process = null;
        }
    }
}
