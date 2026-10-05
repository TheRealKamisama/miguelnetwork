package io.github.therealkamisama.miguelnetwork.discovery;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DiscoveryManifest(
        String protocol,
        String serverId,
        String audience,
        String clientNonce,
        long configEpoch,
        long issuedAt,
        long expiresAt,
        List<DiscoveryRoute> routes,
        String keyId
) {
    public DiscoveryManifest {
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(clientNonce, "clientNonce");
        Objects.requireNonNull(keyId, "keyId");
        routes = List.copyOf(routes);
        if (serverId.isBlank() || audience.isBlank() || clientNonce.isBlank() || keyId.isBlank()) {
            throw new IllegalArgumentException("Manifest identity fields must not be blank");
        }
        if (configEpoch < 0 || expiresAt <= issuedAt || routes.isEmpty()) {
            throw new IllegalArgumentException("Manifest epoch, validity interval or routes are invalid");
        }
    }

    public boolean isValidAt(Instant now, long allowedClockSkewSeconds) {
        long timestamp = now.getEpochSecond();
        return issuedAt <= timestamp + allowedClockSkewSeconds
                && expiresAt >= timestamp - allowedClockSkewSeconds;
    }
}
