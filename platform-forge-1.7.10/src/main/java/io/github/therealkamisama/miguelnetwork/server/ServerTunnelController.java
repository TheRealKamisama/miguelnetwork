package io.github.therealkamisama.miguelnetwork.server;

import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import io.github.therealkamisama.miguelnetwork.config.DeploymentMode;
import io.github.therealkamisama.miguelnetwork.config.ServerConfig;
import io.github.therealkamisama.miguelnetwork.core.ManagedWstunnelProcess;
import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import io.github.therealkamisama.miguelnetwork.core.NativeWstunnel;
import io.github.therealkamisama.miguelnetwork.core.TransportProtocol;
import io.github.therealkamisama.miguelnetwork.core.WstunnelCommands;
import net.minecraft.server.MinecraftServer;
import io.github.therealkamisama.miguelnetwork.PlatformPaths;

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
    private static NativeContext nativeContext;
    private static final java.util.concurrent.ExecutorService NATIVE_UPDATES =
            java.util.concurrent.Executors.newSingleThreadExecutor(Thread.ofVirtual().name("MiguelNetwork-native-state").factory());

    private ServerTunnelController() {
    }

    public static synchronized void start(MinecraftServer server) {
        start(PlatformPaths.gameDirectory(), server.getServerPort());
    }


     static synchronized void start(Path gameDirectory, int minecraftPort) {
        stop();
        if (!ServerConfig.enabled()) {
            MiguelNetwork.LOGGER.info("MiguelNetwork server tunnel is disabled");
            return;
        }
        try {
            NativeContext context = new NativeContext(gameDirectory,
                    ServerEndpointResolver.resolve(gameDirectory, minecraftPort, OptionalInt.empty()));
            nativeContext = context;
            var peer = io.github.therealkamisama.miguelnetwork.interop.ZstdPeer.get();
            if (peer != null && peer.supportsServer()) {
                context.subscription = peer.watchServer(state -> {
                    context.latest = state;
                    context.initial.complete(null);
                    NATIVE_UPDATES.execute(() -> refreshNative(context));
                });
                context.initial.get(3, java.util.concurrent.TimeUnit.SECONDS);
            }
            var endpoints = ServerEndpointResolver.withNative(context.base, context.latest);
            validatePorts(endpoints);
            context.applied = endpoints;
            Path executable = NativeWstunnel.resolve(gameDirectory);
            context.restrictions = gameDirectory.resolve("config/miguelnetwork/generated/restrictions.yaml");
            writeRestrictions(context.restrictions, restrictions(ServerConfig.pathPrefix(), endpoints));
            ServerEndpointResolver.writeDetectedConfiguration(
                    gameDirectory, endpoints, ServerConfig.mode(), ServerConfig.bindHost(), ServerConfig.publicPort());
            context.documents = ServerConfig.discoveryEnabled()
                    ? DiscoveryDocumentProvider.create(gameDirectory, endpoints) : null;
            if (ServerConfig.mode() == DeploymentMode.STANDALONE) {
                startStandalone(executable, context.restrictions, context.documents, endpoints);
            } else {
                startExternalProxy(executable, context.restrictions, context.documents, endpoints);
            }
            context.started = true;
        } catch (Exception exception) {
            stop();
            MiguelNetwork.LOGGER.error("MiguelNetwork server tunnel failed to start", exception);
        }
    }

    private static synchronized void refreshNative(NativeContext context) {
        if (nativeContext != context || !context.started || process == null || !process.isAlive()) return;
        var state = context.latest;
        var endpoints = ServerEndpointResolver.withNative(context.base, state);
        if (endpoints.equals(context.applied)) return;
        try {
            validatePorts(endpoints);
            // Withdraw a changed compression route while forwarding rules are being applied.
            if (context.documents != null) {
                context.documents.update(new ServerEndpointResolver.ResolvedEndpoints(
                        context.applied.configuredServerIp(), context.applied.targetHost(),
                        context.applied.minecraftPort(), null));
            }
            var acknowledgement = process.expectOutput(line -> line.contains("Restrictions config file has been reloaded"));
            try {
                writeRestrictions(context.restrictions, restrictions(ServerConfig.pathPrefix(), endpoints));
                acknowledgement.get(5, java.util.concurrent.TimeUnit.SECONDS);
            } finally { acknowledgement.cancel(false); }
            context.applied = endpoints;
            ServerEndpointResolver.writeDetectedConfiguration(context.directory, endpoints,
                    ServerConfig.mode(), ServerConfig.bindHost(), ServerConfig.publicPort());
            if (context.documents != null) context.documents.update(endpoints);
            MiguelNetwork.LOGGER.info("MiguelNetwork applied native ZstdNet server state generation {}", state.generation());
        } catch (Exception failure) {
            // The public gateway must not advertise targets after a forwarding-rule failure.
            MiguelNetwork.LOGGER.error("MiguelNetwork native server route update failed; stopping gateway", failure);
            stop();
        }
    }

    private static void writeRestrictions(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "restrictions-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    static String restrictions(String prefix, ServerEndpointResolver.ResolvedEndpoints endpoints) {
        List<InetSocketAddress> targets = new ArrayList<>();
        targets.add(InetSocketAddress.createUnresolved(endpoints.targetHost(), endpoints.minecraftPort()));
        if (endpoints.zstdNetPort() != null) {
            targets.add(InetSocketAddress.createUnresolved(endpoints.zstdNetHost(), endpoints.zstdNetPort()));
        }
        StringBuilder result = new StringBuilder();
        for (var target : targets.stream().distinct().toList()) {
            String document = restrictions(prefix, target.getHostString(), List.of(target.getPort()));
            int allow = document.indexOf("      - !Tunnel");
            if (result.isEmpty()) result.append(document, 0, allow);
            result.append(document.substring(allow));
        }
        return result.toString();
    }

    private static final class NativeContext {
        final Path directory;
        final ServerEndpointResolver.ResolvedEndpoints base;
        final java.util.concurrent.CompletableFuture<Void> initial = new java.util.concurrent.CompletableFuture<>();
        volatile io.github.therealkamisama.miguelnetwork.interop.ZstdPeer.ServerState latest =
                io.github.therealkamisama.miguelnetwork.interop.ZstdPeer.ServerState.inactive();
        AutoCloseable subscription;
        ServerEndpointResolver.ResolvedEndpoints applied;
        DiscoveryDocumentProvider documents;
        Path restrictions;
        boolean started;
        NativeContext(Path directory, ServerEndpointResolver.ResolvedEndpoints base) {
            this.directory = directory; this.base = base;
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
                    endpoints.zstdNetHost(), endpoints.zstdNetPort());
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
        NativeContext closing = nativeContext;
        nativeContext = null;
        if (closing != null) io.github.therealkamisama.miguelnetwork.interop.NativeBridge.closeQuietly(closing.subscription);
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
