package io.github.therealkamisama.miguelnetwork.server;

import io.github.therealkamisama.miguelnetwork.config.ServerConfig;
import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryCodec;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryFilter;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryIdentity;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryManifest;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryRoute;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DiscoveryDocumentProvider {
    private final DiscoveryIdentity identity;
    private final String targetHost;
    private final int minecraftPort;
    private final Integer zstdNetPort;

    private DiscoveryDocumentProvider(
            DiscoveryIdentity identity,
            String targetHost,
            int minecraftPort,
            Integer zstdNetPort
    ) {
        this.identity = identity;
        this.targetHost = targetHost;
        this.minecraftPort = minecraftPort;
        this.zstdNetPort = zstdNetPort;
    }

    static DiscoveryDocumentProvider create(
            Path gameDirectory,
            ServerEndpointResolver.ResolvedEndpoints endpoints
    ) throws Exception {
        DiscoveryIdentity identity = ServerConfig.signDiscoveryResponses()
                ? DiscoveryIdentity.loadOrCreate(gameDirectory.resolve("config/miguelnetwork/generated")) : null;
        return new DiscoveryDocumentProvider(
                identity, endpoints.targetHost(), endpoints.minecraftPort(), endpoints.zstdNetPort());
    }

    byte[] response(String rawAudience, byte[] request) throws Exception {
        String nonce = DiscoveryCodec.decodeRequestNonce(request);
        String audience = normalizeAudience(rawAudience);
        HostPort advertised = advertisedEndpoint(audience);
        long issuedAt = Instant.now().getEpochSecond();
        DiscoveryManifest manifest = new DiscoveryManifest(
                MiguelNetworkProtocol.DISCOVERY_PROTOCOL,
                identity == null ? "unsigned" : identity.serverId(),
                audience,
                nonce,
                ServerConfig.discoveryConfigEpoch(),
                issuedAt,
                issuedAt + ServerConfig.discoveryValiditySeconds(),
                routes(advertised),
                identity == null ? "unsigned" : identity.keyId()
        );
        return identity == null
                ? DiscoveryCodec.encodeUnsigned(manifest)
                : DiscoveryCodec.encodeAndSign(manifest, identity.privateKey(), identity.publicKey());
    }

    private List<DiscoveryRoute> routes(HostPort advertised) {
        List<DiscoveryRoute> routes = new ArrayList<>();
        if (zstdNetPort != null && zstdNetPort != minecraftPort) {
            routes.add(new DiscoveryRoute(
                    "zstdnet-primary", ServerConfig.advertisedTransport(), advertised.host(), advertised.port(),
                    ServerConfig.pathPrefix(), targetHost, zstdNetPort, 200,
                    List.of(new DiscoveryFilter(MiguelNetworkProtocol.ZSTDNET_FILTER, 1, true))
            ));
        }
        routes.add(new DiscoveryRoute(
                "minecraft-primary", ServerConfig.advertisedTransport(), advertised.host(), advertised.port(),
                ServerConfig.pathPrefix(), targetHost, minecraftPort, 100, List.of()
        ));
        return routes;
    }

    private static String normalizeAudience(String audience) {
        String result = audience == null ? "" : audience.trim().toLowerCase(Locale.ROOT);
        if (result.isBlank() || result.indexOf('/') >= 0 || result.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Missing or invalid Host header");
        }
        return result;
    }

    private static HostPort advertisedEndpoint(String audience) {
        HostPort parsed = HostPort.parse(audience, ServerConfig.advertisedTransport().usesTls() ? 443 : 80);
        String configuredHost = ServerConfig.advertisedHost();
        int configuredPort = ServerConfig.advertisedPort();
        return new HostPort(configuredHost.isBlank() ? parsed.host() : configuredHost,
                configuredPort == 0 ? parsed.port() : configuredPort);
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
