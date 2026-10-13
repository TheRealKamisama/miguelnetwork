package io.github.therealkamisama.miguelnetwork.client;

import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import io.github.therealkamisama.miguelnetwork.interop.NativeBridge;
import io.github.therealkamisama.miguelnetwork.interop.ZstdPeer;
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
import io.github.therealkamisama.miguelnetwork.PlatformPaths;

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
    private static final int MAX_DISCOVERED_ROUTES = 256;
    private static final Map<String, ClientTunnel> TUNNELS = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<String, CachedManifest> MANIFESTS = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<String, CachedLegacyRoute> LEGACY_ROUTES = new LinkedHashMap<>(16, 0.75f, true);
    private static final java.util.Set<TunnelLease> ACTIVE_LEASES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static ClientTrustStore trustStore;
    private static ClientDiscoveryService discoveryService;
    private static boolean insecureWarningLogged;
    private static boolean plainWsLogged;

    private ClientTunnelManager() {
    }


    /** One composite lease per socket; status requests never close gameplay. */
    public static synchronized TunnelLease acquire(InetSocketAddress original) {
        return acquire(original, () -> false);
    }

    public static synchronized TunnelLease acquire(InetSocketAddress original, java.util.function.BooleanSupplier cancelled) {
        NativeBridge.checkCancelled(cancelled);
        if (!ClientConfig.enabled()) return new TunnelLease(original, null, false, null);
        ZstdPeer peer = ZstdPeer.get();
        if (ownsEndpoint(original) || (peer != null && peer.ownsEndpoint(original))) {
            return new TunnelLease(original, null, false, null, true);
        }
        RoutePlan route = resolveRoute(original).orElse(null);
        NativeBridge.checkCancelled(cancelled);
        if (route == null || route.tcp()) return new TunnelLease(original, null, false, null);
        ClientTunnel tunnel = startOrReuse(original, route, cancelled);
        tunnel.references++;
        ZstdPeer.Stream stream = null;
        try {
            NativeBridge.checkCancelled(cancelled);
            boolean filtered = peer != null && peer.supportsStream()
                    && route.discoveryRoute().filters().stream().anyMatch(filter ->
                    MiguelNetworkProtocol.ZSTDNET_FILTER.equals(filter.id()) && filter.version() == 1);
            if (filtered) {
                if (peer == null) throw new IllegalStateException("Required native Zstd stream unavailable");
                stream = peer.open(tunnel.localAddress(), original);
            }
            NativeBridge.checkCancelled(cancelled);
            var lease = new TunnelLease(stream == null ? tunnel.localAddress() : stream.address(),
                    tunnel, route.discovered(), stream);
            ACTIVE_LEASES.add(lease);
            return lease;
        } catch (RuntimeException | Error failure) {
            NativeBridge.closeQuietly(stream == null ? null : stream.lease());
            release(tunnel);
            throw failure;
        }
    }

    private static boolean ownsEndpoint(InetSocketAddress address) {
        String host = address.getAddress() == null ? address.getHostString() : address.getAddress().getHostAddress();
        if (!(host.equals("127.0.0.1") || host.equalsIgnoreCase("localhost"))) return false;
        return TUNNELS.values().stream().anyMatch(tunnel ->
                tunnel.localAddress().getPort() == address.getPort() && tunnel.process().isAlive());
    }

    private static synchronized void release(ClientTunnel tunnel) {
        if (tunnel == null || --tunnel.references > 0) return;
        TUNNELS.values().removeIf(value -> value == tunnel);
        tunnel.process().close();
    }

    public static final class TunnelLease implements AutoCloseable {
        private final InetSocketAddress address;
        private final ClientTunnel tunnel;
        private final boolean discovered;
        private final ZstdPeer.Stream stream;
        private final boolean routed;
        private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();

        private TunnelLease(InetSocketAddress address, ClientTunnel tunnel, boolean discovered, ZstdPeer.Stream stream) {
            this(address, tunnel, discovered, stream, false);
        }
        private TunnelLease(InetSocketAddress address, ClientTunnel tunnel, boolean discovered,
                            ZstdPeer.Stream stream, boolean internal) {
            this.address = address; this.tunnel = tunnel; this.discovered = discovered; this.stream = stream;
            this.routed = internal || tunnel != null;
        }
        public InetSocketAddress address() { return address; }
        public boolean tunneled() { return tunnel != null; }
        public boolean routed() { return routed; }
        public boolean discovered() { return discovered; }
        public boolean filtered() { return stream != null; }
        public java.util.function.Supplier<Map<String,Object>> stats() { return stream == null ? null : stream.stats(); }
        public Runnable connected() { return stream == null ? () -> {} : stream.connected(); }

        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            ACTIVE_LEASES.remove(this);
            NativeBridge.closeQuietly(stream == null ? null : stream.lease());
            release(tunnel);
        }
    }

    private static Optional<RoutePlan> resolveRoute(InetSocketAddress original) {
        ensureDiscovery();
        String endpoint = endpoint(original);
        CachedManifest cached = MANIFESTS.get(endpoint);
        if (cached != null && !cached.expired()) {
            return Optional.of(requireCompatibleRoute(cached.manifest(), endpoint));
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
        RoutePlan selected = requireCompatibleRoute(manifest, endpoint);
        MiguelNetwork.LOGGER.info("MiguelNetwork selected Discovery route {} for {}",
                selected.discoveryRoute().id(), endpoint);
        return Optional.of(selected);
    }

    private static RoutePlan requireCompatibleRoute(
            DiscoveryManifest manifest,
            String endpoint
    ) {
        return select(manifest, requiresWss(endpoint))
                .orElseThrow(() -> new IllegalStateException(
                        "Discovery returned no compatible route for " + endpoint));
    }

    private static Optional<RoutePlan> select(DiscoveryManifest manifest, boolean requireWss) {
        for (DiscoveryRoute route : manifest.routes()) {
            if (requireWss && !route.transport().usesTls()) {
                continue;
            }
            boolean supported = true;
            for (DiscoveryFilter filter : route.filters()) {
                ZstdPeer peer = ZstdPeer.get();
                boolean filterSupported = peer != null && peer.supportsStream()
                        && MiguelNetworkProtocol.ZSTDNET_FILTER.equals(filter.id()) && filter.version() == 1;
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
            throw new IllegalStateException("MiguelNetwork Discovery unavailable and legacyFallback is disabled for " + endpoint, discoveryFailure);
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

    private static ClientTunnel startOrReuse(InetSocketAddress original, RoutePlan route, java.util.function.BooleanSupplier cancelled) {
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
            if (!route.transport().usesTls() && !plainWsLogged) {
                plainWsLogged = true;
                MiguelNetwork.LOGGER.info("MiguelNetwork client selected plain WS");
            } else if (route.transport().usesTls() && !verifyCertificate && !insecureWarningLogged) {
                insecureWarningLogged = true;
                MiguelNetwork.LOGGER.warn("MiguelNetwork TLS certificate verification is disabled (development only)");
            }
            Path executable = NativeWstunnel.resolve(PlatformPaths.gameDirectory());
            started = ManagedWstunnelProcess.start(
                    WstunnelCommands.client(
                            executable, route.host(), route.port(), localPort, route.targetHost(), route.targetPort(),
                            route.pathPrefix(), route.transport(), verifyCertificate
                    ),
                    line -> line.contains("Starting TCP server listening cnx on"),
                    MiguelNetwork.LOGGER
            );
            started.awaitReady(Duration.ofSeconds(10), cancelled);
            ClientTunnel tunnel = new ClientTunnel(started, localPort);
            TUNNELS.put(tunnelKey, tunnel);
            MiguelNetwork.LOGGER.info("MiguelNetwork redirects {} through route {} to 127.0.0.1:{}",
                    endpoint(original), route.cacheKey(), localPort);
            return tunnel;
        } catch (IOException | RuntimeException exception) {
            if (started != null) {
                started.close();
            }
            throw new IllegalStateException("Cannot prepare MiguelNetwork tunnel for " + endpoint(original), exception);
        }
    }

    private static void ensureDiscovery() {
        if (trustStore == null) {
            Path path = PlatformPaths.gameDirectory().resolve("config/miguelnetwork/client-trust.json");
            trustStore = ClientTrustStore.open(path);
            discoveryService = new ClientDiscoveryService(
                    trustStore, DISCOVERY_TIMEOUT, ClientConfig.verifyDiscoverySignatures());
        }
    }

    private static boolean requiresWss(String endpoint) {
        return ClientConfig.enforceWssDowngradeProtection() && trustStore.requiresWss(endpoint);
    }

    private static void evictToCapacity(int maximum) {
        TUNNELS.entrySet().removeIf(entry -> {
            if (entry.getValue().process().isAlive()) return false;
            entry.getValue().process().close();
            return true;
        });
        if (TUNNELS.size() >= maximum) {
            throw new IllegalStateException("MiguelNetwork tunnel capacity reached; existing connections are retained");
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
        return EndpointMatcher.formatEndpoint(address.getHostString(), address.getPort()).toLowerCase(java.util.Locale.ROOT);
    }

    public static synchronized void stop() {
        for (TunnelLease lease : java.util.List.copyOf(ACTIVE_LEASES)) lease.close();
        for (ClientTunnel tunnel : TUNNELS.values()) {
            tunnel.process().close();
        }
        TUNNELS.clear();
        MANIFESTS.clear();
        LEGACY_ROUTES.clear();
    }

    private static final class ClientTunnel {
        private final ManagedWstunnelProcess process;
        private final int localPort;
        private int references;

        private ClientTunnel(ManagedWstunnelProcess process, int localPort) {
            this.process = process;
            this.localPort = localPort;
        }

        private ManagedWstunnelProcess process() { return process; }
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

    private record RoutePlan(DiscoveryRoute discoveryRoute, boolean tcp, String cacheKey, boolean discovered) {
        private static RoutePlan discovery(DiscoveryRoute route) {
            return new RoutePlan(route, false,
                    "discovery:" + route.id() + ":" + route.transport().scheme() + ":"
                            + route.host() + ":" + route.port() + ":" + route.pathPrefix() + ":"
                            + route.wstunnelTargetHost() + ":" + route.wstunnelTargetPort(), true);
        }

        private static RoutePlan legacy(TransportProtocol transport, InetSocketAddress original) {
            DiscoveryRoute route = new DiscoveryRoute(
                    "legacy-" + transport.scheme(), transport, original.getHostString(), original.getPort(),
                    ClientConfig.pathPrefix(), "127.0.0.1", ClientConfig.targetPort(), 0, java.util.List.of()
            );
            return new RoutePlan(route, false, "legacy:" + transport.scheme(), false);
        }

        private static RoutePlan tcpPlan() {
            return new RoutePlan(null, true, "tcp", false);
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
