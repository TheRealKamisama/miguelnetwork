package io.github.therealkamisama.miguelnetwork.client;

import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import io.github.therealkamisama.miguelnetwork.compat.ZstdNetClientCompatibility;
import io.github.therealkamisama.miguelnetwork.config.ClientConfig;
import io.github.therealkamisama.miguelnetwork.core.EndpointMatcher;
import io.github.therealkamisama.miguelnetwork.core.ManagedWstunnelProcess;
import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import io.github.therealkamisama.miguelnetwork.core.NativeWstunnel;
import io.github.therealkamisama.miguelnetwork.core.TransportProtocol;
import io.github.therealkamisama.miguelnetwork.core.WstunnelCommands;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryFilter;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryManifest;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryRoute;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ClientTunnelManager {
    private static final Duration DISCOVERY_TIMEOUT = Duration.ofMillis(1500);
    private static final Duration TCP_DISCOVERY_TTL = Duration.ofSeconds(30);
    private static final Duration INTERNAL_LOOPBACK_TTL = Duration.ofSeconds(30);
    private static final int MAX_DISCOVERED_ROUTES = 256;
    private static final Map<String, ClientTunnel> TUNNELS = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<String, CachedManifest> MANIFESTS = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<String, CachedLegacyRoute> LEGACY_ROUTES = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<Integer, Long> INTERNAL_LOOPBACK_PORTS = new LinkedHashMap<>();
    private static ClientTrustStore trustStore;
    private static ClientDiscoveryService discoveryService;
    private static boolean insecureWarningLogged;
    private static boolean unencryptedWarningLogged;

    private ClientTunnelManager() {
    }

    public static synchronized InetSocketAddress redirect(InetSocketAddress original) {
        if (!ClientConfig.enabled() || consumeInternalLoopback(original)) {
            return original;
        }
        RoutePlan route = resolveRoute(original, false).orElse(null);
        if (route == null || route.tcp()) {
            return original;
        }
        return startOrReuse(original, route).localAddress();
    }

    public static synchronized Optional<PreparedTunnel> prepareForZstdNet(InetSocketAddress original) {
        if (!ClientConfig.enabled() || !ZstdNetClientCompatibility.isSupported()) {
            return Optional.empty();
        }
        Optional<RoutePlan> selected = resolveRoute(original, true)
                .filter(route -> !route.tcp()
                        && route.discoveryRoute().usesFilter(MiguelNetworkProtocol.ZSTDNET_FILTER));
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        ClientTunnel tunnel = startOrReuse(original, selected.get());
        return Optional.of(new PreparedTunnel(tunnel.localAddress(), selected.get().discoveryRoute()));
    }

    public static synchronized void registerInternalLoopbackPort(int port) {
        if (port > 0 && port <= 65535) {
            removeExpiredInternalPorts();
            INTERNAL_LOOPBACK_PORTS.put(port, System.nanoTime());
            while (INTERNAL_LOOPBACK_PORTS.size() > 256) {
                Iterator<Integer> iterator = INTERNAL_LOOPBACK_PORTS.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }
    }

    private static boolean consumeInternalLoopback(InetSocketAddress address) {
        String host = address.getHostString();
        if (!(host.equals("127.0.0.1") || host.equals("localhost") || host.equals("::1"))) {
            return false;
        }
        removeExpiredInternalPorts();
        return INTERNAL_LOOPBACK_PORTS.remove(address.getPort()) != null;
    }

    private static void removeExpiredInternalPorts() {
        long cutoff = System.nanoTime() - INTERNAL_LOOPBACK_TTL.toNanos();
        INTERNAL_LOOPBACK_PORTS.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    private static Optional<RoutePlan> resolveRoute(InetSocketAddress original, boolean permitZstdNet) {
        ensureDiscovery();
        String endpoint = endpoint(original);
        CachedManifest cached = MANIFESTS.get(endpoint);
        if (cached != null && !cached.expired()) {
            return Optional.of(requireCompatibleRoute(cached.manifest(), permitZstdNet, endpoint));
        }
        MANIFESTS.remove(endpoint);

        DiscoveryManifest manifest;
        try {
            manifest = discoveryService.discover(
                    original.getHostString(), original.getPort(), DISCOVERY_TIMEOUT
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("MiguelNetwork rejected untrusted Discovery data for " + endpoint, exception);
        } catch (IOException exception) {
            MiguelNetwork.LOGGER.debug("MiguelNetwork Discovery unavailable for {}: {}", endpoint, exception.toString());
            return legacyRoute(original, endpoint, exception);
        }
        rememberManifest(endpoint, manifest);
        RoutePlan selected = requireCompatibleRoute(manifest, permitZstdNet, endpoint);
        MiguelNetwork.LOGGER.info("MiguelNetwork selected Discovery route {} for {}",
                selected.discoveryRoute().id(), endpoint);
        return Optional.of(selected);
    }

    private static RoutePlan requireCompatibleRoute(
            DiscoveryManifest manifest,
            boolean permitZstdNet,
            String endpoint
    ) {
        return select(manifest, permitZstdNet, requiresWss(endpoint))
                .orElseThrow(() -> new IllegalStateException(
                        "Discovery returned no compatible route for " + endpoint));
    }

    private static Optional<RoutePlan> select(DiscoveryManifest manifest, boolean permitZstdNet, boolean requireWss) {
        for (DiscoveryRoute route : manifest.routes()) {
            if (requireWss && !route.transport().usesTls()) {
                continue;
            }
            boolean supported = true;
            for (DiscoveryFilter filter : route.filters()) {
                boolean filterSupported = filter.id().equals(MiguelNetworkProtocol.ZSTDNET_FILTER)
                        && filter.version() == 1
                        && permitZstdNet
                        && ZstdNetClientCompatibility.isSupported();
                if (filter.required() && !filterSupported) {
                    supported = false;
                    break;
                }
            }
            if (supported) {
                return Optional.of(RoutePlan.discovery(route));
            }
        }
        return Optional.empty();
    }

    private static Optional<RoutePlan> legacyRoute(
            InetSocketAddress original,
            String endpoint,
            Exception discoveryFailure
    ) {
        if (!ClientConfig.legacyFallback()) {
            if (requiresWss(endpoint)) {
                throw downgradeBlocked(endpoint, discoveryFailure);
            }
            return Optional.empty();
        }
        CachedLegacyRoute cached = LEGACY_ROUTES.get(endpoint);
        if (cached != null && !cached.expired()) {
            if (requiresWss(endpoint)
                    && (cached.route().isEmpty() || !cached.route().get().usesTls())) {
                LEGACY_ROUTES.remove(endpoint);
            } else {
                return cached.route().isPresent()
                        ? Optional.of(RoutePlan.legacy(cached.route().get(), original))
                        : Optional.of(RoutePlan.tcpPlan());
            }
        }
        LEGACY_ROUTES.remove(endpoint);

        Optional<TransportProtocol> forced = ClientConfig.forcedTransport();
        Optional<TransportProtocol> detected;
        if (requiresWss(endpoint)) {
            detected = ClientTransportProbe.detectOnly(
                    original.getHostString(), original.getPort(), ClientConfig.targetPort(),
                    ClientConfig.pathPrefix(), DISCOVERY_TIMEOUT, TransportProtocol.WSS
            );
            if (detected.isEmpty()) {
                throw downgradeBlocked(endpoint, discoveryFailure);
            }
        } else {
            detected = forced.isPresent() ? forced : ClientTransportProbe.detect(
                    original.getHostString(), original.getPort(), ClientConfig.targetPort(),
                    ClientConfig.pathPrefix(), DISCOVERY_TIMEOUT
            );
        }
        rememberLegacy(endpoint, detected);
        if (detected.isPresent()) {
            if (detected.get().usesTls() && ClientConfig.enforceWssDowngradeProtection()) {
                trustStore.recordWss(endpoint);
            }
            MiguelNetwork.LOGGER.info("MiguelNetwork selected legacy {} route for {}", detected.get(), endpoint);
            return Optional.of(RoutePlan.legacy(detected.get(), original));
        }
        MiguelNetwork.LOGGER.info("MiguelNetwork discovered vanilla TCP endpoint {}", endpoint);
        return Optional.of(RoutePlan.tcpPlan());
    }

    private static IllegalStateException downgradeBlocked(String endpoint, Exception cause) {
        return new IllegalStateException(
                "MiguelNetwork blocked an insecure downgrade for known-WSS endpoint " + endpoint, cause
        );
    }

    private static ClientTunnel startOrReuse(InetSocketAddress original, RoutePlan route) {
        String tunnelKey = endpoint(original) + "|" + route.cacheKey();
        ClientTunnel existing = TUNNELS.get(tunnelKey);
        if (existing != null && existing.process().isAlive()) {
            return existing;
        }
        closeAndRemove(tunnelKey);

        ManagedWstunnelProcess started = null;
        try {
            evictToCapacity(ClientConfig.maxTunnelProcesses());
            int localPort = findCandidatePort();
            boolean verifyCertificate = ClientConfig.verifyCertificate();
            if (!route.transport().usesTls() && !unencryptedWarningLogged) {
                unencryptedWarningLogged = true;
                MiguelNetwork.LOGGER.warn("MiguelNetwork client selected unencrypted WS; use only on a trusted network");
            } else if (!verifyCertificate && !insecureWarningLogged) {
                insecureWarningLogged = true;
                MiguelNetwork.LOGGER.warn("MiguelNetwork TLS certificate verification is disabled (development only)");
            }
            Path executable = NativeWstunnel.resolve(FMLPaths.GAMEDIR.get());
            started = ManagedWstunnelProcess.start(
                    WstunnelCommands.client(
                            executable, route.host(), route.port(), localPort, route.targetHost(), route.targetPort(),
                            route.pathPrefix(), route.transport(), verifyCertificate
                    ),
                    line -> line.contains("Starting TCP server listening cnx on"),
                    MiguelNetwork.LOGGER
            );
            started.awaitReady(Duration.ofSeconds(10));
            ClientTunnel tunnel = new ClientTunnel(started, localPort);
            TUNNELS.put(tunnelKey, tunnel);
            MiguelNetwork.LOGGER.info("MiguelNetwork redirects {} through route {} to 127.0.0.1:{}",
                    endpoint(original), route.cacheKey(), localPort);
            return tunnel;
        } catch (IOException exception) {
            if (started != null) {
                started.close();
            }
            throw new IllegalStateException("Cannot prepare MiguelNetwork tunnel for " + endpoint(original), exception);
        }
    }

    private static void ensureDiscovery() {
        if (trustStore == null) {
            Path path = FMLPaths.GAMEDIR.get().resolve("config/miguelnetwork/client-trust.json");
            trustStore = ClientTrustStore.open(path);
            discoveryService = new ClientDiscoveryService(
                    trustStore, DISCOVERY_TIMEOUT, ClientConfig.verifyDiscoverySignatures());
        }
    }

    private static boolean requiresWss(String endpoint) {
        return ClientConfig.enforceWssDowngradeProtection() && trustStore.requiresWss(endpoint);
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

    private static void closeAndRemove(String key) {
        ClientTunnel removed = TUNNELS.remove(key);
        if (removed != null) {
            removed.process().close();
        }
    }

    private static void rememberManifest(String endpoint, DiscoveryManifest manifest) {
        MANIFESTS.put(endpoint, new CachedManifest(manifest));
        trim(MANIFESTS);
    }

    private static void rememberLegacy(String endpoint, Optional<TransportProtocol> route) {
        LEGACY_ROUTES.put(endpoint, new CachedLegacyRoute(route, System.nanoTime()));
        trim(LEGACY_ROUTES);
    }

    private static void trim(Map<String, ?> map) {
        while (map.size() > MAX_DISCOVERED_ROUTES) {
            Iterator<String> iterator = map.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private static int findCandidatePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        }
    }

    private static String endpoint(InetSocketAddress address) {
        return EndpointMatcher.formatEndpoint(address.getHostString(), address.getPort()).toLowerCase();
    }

    public static synchronized void stop() {
        for (ClientTunnel tunnel : TUNNELS.values()) {
            tunnel.process().close();
        }
        TUNNELS.clear();
        MANIFESTS.clear();
        LEGACY_ROUTES.clear();
        INTERNAL_LOOPBACK_PORTS.clear();
    }

    public record PreparedTunnel(InetSocketAddress address, DiscoveryRoute route) {
    }

    private record ClientTunnel(ManagedWstunnelProcess process, int localPort) {
        private InetSocketAddress localAddress() {
            return new InetSocketAddress("127.0.0.1", localPort);
        }
    }

    private record CachedManifest(DiscoveryManifest manifest) {
        private boolean expired() {
            return manifest.expiresAt() <= Instant.now().getEpochSecond();
        }
    }

    private record CachedLegacyRoute(Optional<TransportProtocol> route, long discoveredAtNanos) {
        private boolean expired() {
            return route.isEmpty() && System.nanoTime() - discoveredAtNanos >= TCP_DISCOVERY_TTL.toNanos();
        }
    }

    private record RoutePlan(DiscoveryRoute discoveryRoute, boolean tcp, String cacheKey) {
        private static RoutePlan discovery(DiscoveryRoute route) {
            return new RoutePlan(route, false,
                    "discovery:" + route.id() + ":" + route.transport().scheme() + ":"
                            + route.host() + ":" + route.port() + ":" + route.pathPrefix() + ":"
                            + route.wstunnelTargetHost() + ":" + route.wstunnelTargetPort());
        }

        private static RoutePlan legacy(TransportProtocol transport, InetSocketAddress original) {
            DiscoveryRoute route = new DiscoveryRoute(
                    "legacy-" + transport.scheme(), transport, original.getHostString(), original.getPort(),
                    ClientConfig.pathPrefix(), "127.0.0.1", ClientConfig.targetPort(), 0, java.util.List.of()
            );
            return new RoutePlan(route, false, "legacy:" + transport.scheme());
        }

        private static RoutePlan tcpPlan() {
            return new RoutePlan(null, true, "tcp");
        }

        private TransportProtocol transport() {
            return discoveryRoute.transport();
        }

        private String host() {
            return discoveryRoute.host();
        }

        private int port() {
            return discoveryRoute.port();
        }

        private int targetPort() {
            return discoveryRoute.wstunnelTargetPort();
        }

        private String targetHost() {
            return discoveryRoute.wstunnelTargetHost();
        }

        private String pathPrefix() {
            return discoveryRoute.pathPrefix();
        }
    }
}
