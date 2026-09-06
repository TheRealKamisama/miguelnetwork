package io.github.therealkamisama.miguelnetwork.discovery;

import java.security.PublicKey;

public record SignedDiscoveryDocument(
        DiscoveryManifest manifest,
        byte[] payload,
        byte[] signature,
        PublicKey publicKey
) {
    public SignedDiscoveryDocument {
        payload = payload.clone();
        signature = signature.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }
}
