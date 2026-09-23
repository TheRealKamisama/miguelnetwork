package io.github.therealkamisama.miguelnetwork.client;

import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryFilter;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryManifest;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryRoute;
import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import java.util.Optional;

/** Selects an advertised route without ever inventing an unadvertised target. */
final class ClientRouteSelector {
    private ClientRouteSelector() {}

    static Optional<DiscoveryRoute> select(DiscoveryManifest manifest, boolean requireWss) {
        return select(manifest, requireWss, false);
    }

    static Optional<DiscoveryRoute> select(
            DiscoveryManifest manifest,
            boolean requireWss,
            boolean zstdNetAdapterAvailable
    ) {
        return manifest.routes().stream()
                .filter(route -> !requireWss || route.transport().usesTls())
                .filter(route -> route.filters().stream().noneMatch(
                        filter -> filter.required() && !supports(filter, zstdNetAdapterAvailable)))
                .findFirst();
    }

    private static boolean supports(DiscoveryFilter filter, boolean zstdNetAdapterAvailable) {
        return zstdNetAdapterAvailable
                && MiguelNetworkProtocol.ZSTDNET_FILTER.equals(filter.id())
                && filter.version() == 1;
    }
}
