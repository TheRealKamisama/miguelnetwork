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
        Path first = resolveBundled();
        String firstHash = NativeWstunnel.sha256(first);
        Path second = resolveBundled();

        assertTrue(Files.isRegularFile(first));
        assertEquals(first, second);
        assertEquals(firstHash, NativeWstunnel.sha256(second));
        assertTrue(first.toString().contains(WstunnelManifest.load().version()));
    }

    private Path resolveBundled() throws Exception {
        return NativeWstunnel.resolve(gameDirectory, null, OperatingSystem.current(), false,
                WstunnelManifest.load(), NativeWstunnel.class::getResourceAsStream,
                (uri, target) -> { throw new AssertionError("Bundled distribution must not download"); });
    }
}

