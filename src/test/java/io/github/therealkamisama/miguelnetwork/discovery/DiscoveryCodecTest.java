package io.github.therealkamisama.miguelnetwork.discovery;

import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import io.github.therealkamisama.miguelnetwork.core.TransportProtocol;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiscoveryCodecTest {
    @Test
    void roundTripsAndVerifiesSignedManifest() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        DiscoveryManifest manifest = manifest(pair, 7, "nonce-value");

        byte[] encoded = DiscoveryCodec.encodeAndSign(manifest, pair.getPrivate(), pair.getPublic());
        SignedDiscoveryDocument decoded = DiscoveryCodec.decodeAndVerify(encoded);

        assertEquals(manifest, decoded.manifest());
        assertArrayEquals(pair.getPublic().getEncoded(), decoded.publicKey().getEncoded());
        assertEquals("192.168.0.146", decoded.manifest().routes().get(0).wstunnelTargetHost());
        assertEquals(25573, decoded.manifest().routes().get(0).wstunnelTargetPort());
    }

    @Test
    void rejectsPayloadTampering() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] encoded = DiscoveryCodec.encodeAndSign(
                manifest(pair, 1, "nonce-value"), pair.getPrivate(), pair.getPublic()
        );
        String json = new String(encoded);
        int payloadStart = json.indexOf("\"payload\":\"") + "\"payload\":\"".length();
        char replacement = json.charAt(payloadStart) == 'A' ? 'B' : 'A';
        byte[] tampered = (json.substring(0, payloadStart) + replacement + json.substring(payloadStart + 1)).getBytes();

        assertThrows(GeneralSecurityException.class, () -> DiscoveryCodec.decodeAndVerify(tampered));
    }

    @Test
    void roundTripsUnsignedManifestWhenVerificationIsOptional() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        DiscoveryManifest manifest = manifest(pair, 1, "nonce-value");

        assertEquals(manifest, DiscoveryCodec.decodeWithoutSignatureVerification(
                DiscoveryCodec.encodeUnsigned(manifest)));
        assertThrows(GeneralSecurityException.class,
                () -> DiscoveryCodec.decodeAndVerify(DiscoveryCodec.encodeUnsigned(manifest)));
    }

    @Test
    void requiresA256BitRequestNonce() {
        String shortNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[8]);
        byte[] request = DiscoveryCodec.encodeRequest(shortNonce);

        assertThrows(IllegalArgumentException.class, () -> DiscoveryCodec.decodeRequestNonce(request));
    }

    private static DiscoveryManifest manifest(KeyPair pair, long epoch, String nonce) throws Exception {
        long now = Instant.now().getEpochSecond();
        return new DiscoveryManifest(
                MiguelNetworkProtocol.DISCOVERY_PROTOCOL,
                "bd7fe23d-d0e3-41b3-98d1-a16bea52c3ff",
                "example.test:35548",
                nonce,
                epoch,
                now,
                now + 120,
                List.of(new DiscoveryRoute(
                        "zstdnet-primary", TransportProtocol.WSS, "edge.example.test", 443,
                        "miguelnetwork-v1", "192.168.0.146", 25573, 200,
                        List.of(new DiscoveryFilter(MiguelNetworkProtocol.ZSTDNET_FILTER, 1, true))
                )),
                DiscoveryCodec.keyId(pair.getPublic())
        );
    }
}
