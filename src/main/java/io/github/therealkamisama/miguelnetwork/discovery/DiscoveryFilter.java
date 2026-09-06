package io.github.therealkamisama.miguelnetwork.discovery;

import java.util.Objects;

public record DiscoveryFilter(String id, int version, boolean required) {
    public DiscoveryFilter {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Filter id must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("Filter version must be positive");
        }
    }
}
