package io.github.therealkamisama.miguelnetwork.discovery;

import io.github.therealkamisama.miguelnetwork.core.TransportProtocol;

import java.util.List;
import java.util.Objects;

public record DiscoveryRoute(
        String id,
        TransportProtocol transport,
        String host,
        int port,
        String pathPrefix,
        String wstunnelTargetHost,
        int wstunnelTargetPort,
        int priority,
        List<DiscoveryFilter> filters
) {
    public DiscoveryRoute {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(transport, "transport");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(pathPrefix, "pathPrefix");
        Objects.requireNonNull(wstunnelTargetHost, "wstunnelTargetHost");
        filters = List.copyOf(filters);
        if (id.isBlank() || host.isBlank() || pathPrefix.isBlank() || wstunnelTargetHost.isBlank()) {
            throw new IllegalArgumentException("Route id, host, pathPrefix and target host must not be blank");
        }
        if (port < 1 || port > 65535 || wstunnelTargetPort < 1 || wstunnelTargetPort > 65535) {
            throw new IllegalArgumentException("Route ports must be in the range 1..65535");
        }
    }

    public boolean usesFilter(String filterId) {
        return filters.stream().anyMatch(filter -> filter.id().equals(filterId));
    }
}
