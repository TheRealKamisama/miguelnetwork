package io.github.therealkamisama.miguelnetwork.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscoveryIdentityTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsServerIdAndEd25519KeyPair() throws Exception {
        DiscoveryIdentity first = DiscoveryIdentity.loadOrCreate(temporaryDirectory);
        DiscoveryIdentity second = DiscoveryIdentity.loadOrCreate(temporaryDirectory);

        assertEquals(first.serverId(), second.serverId());
        assertEquals(first.keyId(), second.keyId());
        assertArrayEquals(first.privateKey().getEncoded(), second.privateKey().getEncoded());
        assertArrayEquals(first.publicKey().getEncoded(), second.publicKey().getEncoded());
    }
}
