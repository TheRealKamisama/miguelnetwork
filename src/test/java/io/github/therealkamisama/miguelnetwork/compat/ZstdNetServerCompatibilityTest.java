package io.github.therealkamisama.miguelnetwork.compat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZstdNetServerCompatibilityTest {
    @ParameterizedTest
    @ValueSource(strings = {"1.4.7", "1.4.8"})
    void acceptsVerifiedVersions(String version) {
        assertTrue(ZstdNetServerCompatibility.isSupportedVersion(version));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.4.6", "1.4.9", "1.4.8-beta"})
    void rejectsUnverifiedVersions(String version) {
        assertFalse(ZstdNetServerCompatibility.isSupportedVersion(version));
    }
}
