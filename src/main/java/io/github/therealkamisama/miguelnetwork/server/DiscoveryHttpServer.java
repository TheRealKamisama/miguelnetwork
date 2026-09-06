package io.github.therealkamisama.miguelnetwork.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import io.github.therealkamisama.miguelnetwork.config.ServerConfig;
import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryCodec;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryFilter;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryIdentity;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryManifest;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryRoute;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class DiscoveryHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;
    private final DiscoveryIdentity identity;
    private final int minecraftPort;
    private final Integer zstdNetPort;

    private DiscoveryHttpServer(
            HttpServer server,
            ExecutorService executor,
            DiscoveryIdentity identity,
            int minecraftPort,
            Integer zstdNetPort
    ) {
        this.server = server;
        this.executor = executor;
        this.identity = identity;
        this.minecraftPort = minecraftPort;
        this.zstdNetPort = zstdNetPort;
    }

    static DiscoveryHttpServer start(Path gameDirectory, int minecraftPort, Integer zstdNetPort) throws Exception {
        DiscoveryIdentity identity = DiscoveryIdentity.loadOrCreate(
                gameDirectory.resolve("config/miguelnetwork/generated")
        );
        InetSocketAddress address = new InetSocketAddress(ServerConfig.discoveryBindHost(), ServerConfig.discoveryPort());
        HttpServer httpServer = HttpServer.create(address, 16);
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "MiguelNetwork-discovery-http");
            thread.setDaemon(true);
            return thread;
        });
        DiscoveryHttpServer result = new DiscoveryHttpServer(
                httpServer, executor, identity, minecraftPort, zstdNetPort
        );
        httpServer.createContext(MiguelNetworkProtocol.DISCOVERY_PATH, result::handle);
        httpServer.setExecutor(executor);
        httpServer.start();
        return result;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestURI().getPath().equals(MiguelNetworkProtocol.DISCOVERY_PATH)) {
                send(exchange, 404, "not found\n".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.getResponseHeaders().set("Allow", "POST");
                send(exchange, 405, "method not allowed\n".getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] body = exchange.getRequestBody().readNBytes(4097);
            String nonce = DiscoveryCodec.decodeRequestNonce(body);
            String audience = requestAudience(exchange);
            HostPort advertised = advertisedEndpoint(audience);
            long issuedAt = Instant.now().getEpochSecond();
            DiscoveryManifest manifest = new DiscoveryManifest(
                    MiguelNetworkProtocol.DISCOVERY_PROTOCOL,
                    identity.serverId(),
                    audience,
                    nonce,
                    ServerConfig.discoveryConfigEpoch(),
                    issuedAt,
                    issuedAt + ServerConfig.discoveryValiditySeconds(),
                    routes(advertised),
                    identity.keyId()
            );
            byte[] response = DiscoveryCodec.encodeAndSign(
                    manifest, identity.privateKey(), identity.publicKey()
            );
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            send(exchange, 200, response);
        } catch (IllegalArgumentException exception) {
            sendIfPossible(exchange, 400, "invalid discovery request\n");
        } catch (Exception exception) {
            MiguelNetwork.LOGGER.warn("Cannot answer MiguelNetwork Discovery request", exception);
            sendIfPossible(exchange, 500, "discovery unavailable\n");
        } finally {
            exchange.close();
        }
    }

    private List<DiscoveryRoute> routes(HostPort advertised) {
        List<DiscoveryRoute> routes = new ArrayList<>();
        if (zstdNetPort != null && zstdNetPort != minecraftPort) {
            routes.add(new DiscoveryRoute(
                    "zstdnet-primary",
                    ServerConfig.advertisedTransport(),
                    advertised.host(),
                    advertised.port(),
                    ServerConfig.pathPrefix(),
                    zstdNetPort,
                    200,
                    List.of(new DiscoveryFilter(MiguelNetworkProtocol.ZSTDNET_FILTER, 1, true))
            ));
        }
        routes.add(new DiscoveryRoute(
                "minecraft-primary",
                ServerConfig.advertisedTransport(),
                advertised.host(),
                advertised.port(),
                ServerConfig.pathPrefix(),
                minecraftPort,
                100,
                List.of()
        ));
        return routes;
    }

    private static String requestAudience(HttpExchange exchange) {
        String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-Host");
        String host = forwarded == null || forwarded.isBlank()
                ? exchange.getRequestHeaders().getFirst("Host")
                : forwarded.split(",", 2)[0].trim();
        if (host == null || host.isBlank() || host.indexOf('/') >= 0 || host.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Missing or invalid Host header");
        }
        return host.toLowerCase(Locale.ROOT);
    }

    private static HostPort advertisedEndpoint(String audience) {
        HostPort parsed = HostPort.parse(audience, ServerConfig.advertisedTransport().usesTls() ? 443 : 80);
        String configuredHost = ServerConfig.advertisedHost();
        int configuredPort = ServerConfig.advertisedPort();
        return new HostPort(configuredHost.isBlank() ? parsed.host() : configuredHost,
                configuredPort == 0 ? parsed.port() : configuredPort);
    }

    private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static void sendIfPossible(HttpExchange exchange, int status, String message) {
        try {
            send(exchange, status, message.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private record HostPort(String host, int port) {
        private static HostPort parse(String endpoint, int defaultPort) {
            String value = endpoint.trim();
            if (value.startsWith("[")) {
                int end = value.indexOf(']');
                if (end < 0) {
                    throw new IllegalArgumentException("Invalid IPv6 endpoint");
                }
                int port = end + 1 < value.length() && value.charAt(end + 1) == ':'
                        ? Integer.parseInt(value.substring(end + 2)) : defaultPort;
                return new HostPort(value.substring(1, end), port);
            }
            int first = value.indexOf(':');
            int last = value.lastIndexOf(':');
            if (first > 0 && first == last) {
                return new HostPort(value.substring(0, last), Integer.parseInt(value.substring(last + 1)));
            }
            return new HostPort(value, defaultPort);
        }
    }
}
