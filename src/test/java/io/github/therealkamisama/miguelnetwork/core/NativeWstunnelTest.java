package io.github.therealkamisama.miguelnetwork.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeWstunnelTest {
    @TempDir
    Path gameDirectory;

    @Test
    void extractsAndReusesVerifiedBundledBinary() throws Exception {
        Path first = NativeWstunnel.resolve(gameDirectory);
        String firstHash = NativeWstunnel.sha256(first);
        Path second = NativeWstunnel.resolve(gameDirectory);

        assertTrue(Files.isRegularFile(first));
        assertEquals(first, second);
        assertEquals(firstHash, NativeWstunnel.sha256(second));
        assertTrue(first.toString().contains(NativeWstunnel.VERSION));
    }
}

