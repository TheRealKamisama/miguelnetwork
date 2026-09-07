package io.github.therealkamisama.miguelnetwork.client;

import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import io.github.therealkamisama.miguelnetwork.core.TransportProtocol;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryCodec;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryManifest;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryRoute;
import io.github.therealkamisama.miguelnetwork.discovery.SignedDiscoveryDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientTrustStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void pinsIdentityRejectsRollbackAndPersistsWssFloor() throws Exception {
        Path path = temporaryDirectory.resolve("trust.json");
        ClientTrustStore trust = ClientTrustStore.open(path);
        KeyPair first = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair replacement = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();

        trust.accept("example.test:35548", document(first, 5));
        assertTrue(trust.requiresWss("example.test:35548"));
        assertThrows(GeneralSecurityException.class,
                () -> trust.accept("example.test:35548", document(first, 4)));
        assertThrows(GeneralSecurityException.class,
                () -> trust.accept("example.test:35548", document(replacement, 6)));
        assertTrue(ClientTrustStore.open(path).requiresWss("example.test:35548"));
    }

    @Test
    void remembersLegacyWssBeforeAKeyIsPinned() throws Exception {
        Path path = temporaryDirectory.resolve("legacy-trust.json");
        ClientTrustStore trust = ClientTrustStore.open(path);
        trust.recordWss("legacy.example:443");

        assertTrue(ClientTrustStore.open(path).requiresWss("legacy.example:443"));
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        trust.accept("legacy.example:443", document(pair, 1));
    }

    private static SignedDiscoveryDocument document(KeyPair pair, long epoch) throws Exception {
        long now = Instant.now().getEpochSecond();
        DiscoveryManifest manifest = new DiscoveryManifest(
                MiguelNetworkProtocol.DISCOVERY_PROTOCOL,
                "6be27b78-3aab-4397-92cd-5d40cd14a45a",
                "example.test:35548",
                "nonce",
                epoch,
                now,
                now + 120,
                List.of(new DiscoveryRoute(
                        "primary", TransportProtocol.WSS, "example.test", 35548,
                        "miguelnetwork-v1", "127.0.0.1", 25566, 100, List.of()
                )),
                DiscoveryCodec.keyId(pair.getPublic())
        );
        return DiscoveryCodec.decodeAndVerify(
                DiscoveryCodec.encodeAndSign(manifest, pair.getPrivate(), pair.getPublic())
        );
    }
}
