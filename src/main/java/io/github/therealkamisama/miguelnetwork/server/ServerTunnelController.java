package io.github.therealkamisama.miguelnetwork.server;

import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import io.github.therealkamisama.miguelnetwork.compat.ZstdNetServerCompatibility;
import io.github.therealkamisama.miguelnetwork.config.DeploymentMode;
import io.github.therealkamisama.miguelnetwork.config.ServerConfig;
import io.github.therealkamisama.miguelnetwork.core.ManagedWstunnelProcess;
import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import io.github.therealkamisama.miguelnetwork.core.NativeWstunnel;
import io.github.therealkamisama.miguelnetwork.core.TransportProtocol;
import io.github.therealkamisama.miguelnetwork.core.WstunnelCommands;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
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
    private static StandaloneGateway standaloneGateway;

    private ServerTunnelController() {
    }

    public static synchronized void start(MinecraftServer server) {
        if (!ServerConfig.enabled()) {
            MiguelNetwork.LOGGER.info("MiguelNetwork server tunnel is disabled");
            return;
        }
        stop();
        try {
            Path gameDirectory = FMLPaths.GAMEDIR.get();
            OptionalInt detectedZstdPort = ZstdNetServerCompatibility.discoverListenPort();
            ServerEndpointResolver.ResolvedEndpoints endpoints = ServerEndpointResolver.resolve(
                    gameDirectory, server.getPort(), detectedZstdPort);
            validatePorts(endpoints);

            List<Integer> allowedTargetPorts = new ArrayList<>();
            allowedTargetPorts.add(endpoints.minecraftPort());
            if (endpoints.zstdNetPort() != null && endpoints.zstdNetPort() != endpoints.minecraftPort()) {
                allowedTargetPorts.add(endpoints.zstdNetPort());
            }
            Path executable = NativeWstunnel.resolve(gameDirectory);
            Path generated = gameDirectory.resolve("config/miguelnetwork/generated/restrictions.yaml");
            Files.createDirectories(generated.getParent());
            Files.writeString(generated,
                    restrictions(ServerConfig.pathPrefix(), endpoints.targetHost(), allowedTargetPorts),
                    StandardCharsets.UTF_8);
            ServerEndpointResolver.writeDetectedConfiguration(
                    gameDirectory, endpoints, ServerConfig.mode(), ServerConfig.bindHost(), ServerConfig.publicPort());

            DiscoveryDocumentProvider documents = ServerConfig.discoveryEnabled()
                    ? DiscoveryDocumentProvider.create(gameDirectory, endpoints) : null;
            if (ServerConfig.mode() == DeploymentMode.STANDALONE) {
                startStandalone(executable, generated, documents, endpoints);
            } else {
                startExternalProxy(executable, generated, documents, endpoints);
            }
        } catch (Exception exception) {
            stop();
            MiguelNetwork.LOGGER.error("MiguelNetwork server tunnel failed to start", exception);
        }
    }

    private static void startStandalone(
            Path executable,
            Path restrictions,
            DiscoveryDocumentProvider documents,
            ServerEndpointResolver.ResolvedEndpoints endpoints
    ) throws Exception {
        int sidecarPort = findCandidatePort();
        process = ManagedWstunnelProcess.start(
                WstunnelCommands.server(executable, "127.0.0.1", sidecarPort, restrictions,
                        TransportProtocol.WS, null, null),
                line -> line.contains("Starting wstunnel server listening on"),
                MiguelNetwork.LOGGER
        );
        process.awaitReady(Duration.ofSeconds(10));
        standaloneGateway = StandaloneGateway.start(
                ServerConfig.bindHost(), ServerConfig.publicPort(), "127.0.0.1", sidecarPort,
                documents == null ? null : documents::response
        );
        MiguelNetwork.LOGGER.info(
                "MiguelNetwork standalone WS gateway is ready on {}:{}; Discovery and wstunnel share one port",
                ServerConfig.bindHost(), ServerConfig.publicPort());
        logDetectedEndpoints(endpoints);
    }

    private static void startExternalProxy(
            Path executable,
            Path restrictions,
            DiscoveryDocumentProvider documents,
            ServerEndpointResolver.ResolvedEndpoints endpoints
    ) throws Exception {
        process = ManagedWstunnelProcess.start(
                WstunnelCommands.server(executable, ServerConfig.bindHost(), ServerConfig.publicPort(), restrictions,
                        TransportProtocol.WS, null, null),
                line -> line.contains("Starting wstunnel server listening on"),
                MiguelNetwork.LOGGER
        );
        process.awaitReady(Duration.ofSeconds(10));
        MiguelNetwork.LOGGER.info("MiguelNetwork private WS upstream is ready on {}:{}",
                ServerConfig.bindHost(), ServerConfig.publicPort());
        if (documents != null) {
            discoveryServer = DiscoveryHttpServer.start(documents);
            MiguelNetwork.LOGGER.info("MiguelNetwork private Discovery upstream is ready on http://{}:{}{}",
                    ServerConfig.discoveryBindHost(), ServerConfig.discoveryPort(),
                    MiguelNetworkProtocol.DISCOVERY_PATH);
        }
        logDetectedEndpoints(endpoints);
    }

    private static void logDetectedEndpoints(ServerEndpointResolver.ResolvedEndpoints endpoints) {
        MiguelNetwork.LOGGER.info("MiguelNetwork detected Minecraft target {}:{} from server.properties/runtime",
                endpoints.targetHost(), endpoints.minecraftPort());
        if (endpoints.zstdNetPort() != null) {
            MiguelNetwork.LOGGER.info("MiguelNetwork advertises detected ZstdNet target {}:{}",
                    endpoints.targetHost(), endpoints.zstdNetPort());
        }
        MiguelNetwork.LOGGER.info("MiguelNetwork Discovery signatures are {}",
                ServerConfig.signDiscoveryResponses() ? "enabled" : "disabled");
    }

    private static void validatePorts(ServerEndpointResolver.ResolvedEndpoints endpoints) throws IOException {
        if (ServerConfig.publicPort() == endpoints.minecraftPort()
                || endpoints.zstdNetPort() != null && ServerConfig.publicPort() == endpoints.zstdNetPort()) {
            throw new IOException("MiguelNetwork publicPort must differ from the Minecraft and ZstdNet listener ports");
        }
        if (ServerConfig.mode() == DeploymentMode.EXTERNAL_PROXY && ServerConfig.discoveryEnabled()
                && ServerConfig.publicPort() == ServerConfig.discoveryPort()
                && ServerConfig.bindHost().equalsIgnoreCase(ServerConfig.discoveryBindHost())) {
            throw new IOException("External-proxy wstunnel and Discovery upstreams cannot bind the same address/port");
        }
    }

    private static int findCandidatePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        }
    }

    static String restrictions(String pathPrefix, int targetPort) {
        return restrictions(pathPrefix, "127.0.0.1", List.of(targetPort));
    }

    static String restrictions(String pathPrefix, List<Integer> targetPorts) {
        return restrictions(pathPrefix, "127.0.0.1", targetPorts);
    }

    static String restrictions(String pathPrefix, String targetHost, List<Integer> targetPorts) {
        String escapedPrefix = yaml(regex(pathPrefix));
        String normalizedTarget = stripBrackets(targetHost.trim());
        StringBuilder result = new StringBuilder("restrictions:\n")
                .append("  - name: \"MiguelNetwork Minecraft only\"\n")
                .append("    description: \"Only TCP forwarding to approved server listeners\"\n")
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
        String hostPattern = isIpLiteral(normalizedTarget) ? "^$" : "^" + regex(normalizedTarget) + "$";
        result.append("        host: \"").append(yaml(hostPattern)).append("\"\n")
                .append("        cidr:\n")
                .append("          - \"127.0.0.1/32\"\n")
                .append("          - \"::1/128\"\n");
        if (isIpv4Literal(normalizedTarget) && !normalizedTarget.equals("127.0.0.1")) {
            result.append("          - \"").append(normalizedTarget).append("/32\"\n");
        } else if (normalizedTarget.indexOf(':') >= 0 && !normalizedTarget.equals("::1")) {
            result.append("          - \"").append(normalizedTarget).append("/128\"\n");
        }
        return result.toString();
    }

    private static boolean isIpLiteral(String value) {
        return isIpv4Literal(value) || value.indexOf(':') >= 0;
    }

    private static boolean isIpv4Literal(String value) {
        return value.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}");
    }

    private static String stripBrackets(String value) {
        return value.startsWith("[") && value.endsWith("]")
                ? value.substring(1, value.length() - 1) : value;
    }

    private static String regex(String value) {
        return value.replaceAll("([\\\\.\\[\\]{}()*+?^$|])", "\\\\$1");
    }

    private static String yaml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static synchronized void stop() {
        if (standaloneGateway != null) {
            standaloneGateway.close();
            standaloneGateway = null;
        }
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
