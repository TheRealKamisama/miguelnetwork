package io.github.therealkamisama.miguelnetwork.server;

import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryCodec;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryManifest;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryRoute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryDocumentProviderTest {
    @TempDir
    Path directory;

    @Test
    void advertisesZstdNetBeforeMinecraftWhenListenerIsDetected() throws Exception {
        var endpoints = new ServerEndpointResolver.ResolvedEndpoints(
                "", "127.0.0.1", 25565, 25566);
        DiscoveryDocumentProvider provider = DiscoveryDocumentProvider.create(directory, endpoints);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

        byte[] response = provider.response(
                "play.example.test:35548", DiscoveryCodec.encodeRequest(nonce));
        DiscoveryManifest manifest = DiscoveryCodec.decodeWithoutSignatureVerification(response);

        assertEquals(2, manifest.routes().size());
        DiscoveryRoute zstd = manifest.routes().get(0);
        assertEquals("zstdnet-primary", zstd.id());
        assertEquals(200, zstd.priority());
        assertEquals(25566, zstd.wstunnelTargetPort());
        assertEquals(1, zstd.filters().size());
        assertEquals(MiguelNetworkProtocol.ZSTDNET_FILTER, zstd.filters().get(0).id());
        assertEquals(1, zstd.filters().get(0).version());
        assertTrue(zstd.filters().get(0).required());
        assertEquals("minecraft-primary", manifest.routes().get(1).id());
    }
}
