package io.github.therealkamisama.miguelnetwork.client;

import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryFilter;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryManifest;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryRoute;
import java.util.Optional;

/** This platform implements the base byte-stream transport, with no filter adapters. */
final class ClientRouteSelector {
    private ClientRouteSelector() {}

    static Optional<DiscoveryRoute> select(DiscoveryManifest manifest, boolean requireWss) {
        return manifest.routes().stream()
                .filter(route -> !requireWss || route.transport().usesTls())
                .filter(route -> route.filters().stream().noneMatch(DiscoveryFilter::required))
                .findFirst();
    }
}