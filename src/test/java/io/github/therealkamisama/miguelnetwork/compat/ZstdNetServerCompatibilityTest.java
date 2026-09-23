package io.github.therealkamisama.miguelnetwork.compat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZstdNetServerCompatibilityTest {
    @ParameterizedTest
    @ValueSource(strings = {"1.4.7-cleanroom-alpha.1"})
    void acceptsTheVerifiedCleanroomVersion(String version) {
        assertTrue(ZstdNetServerCompatibility.isSupportedVersion(version));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.4.7", "1.4.7-cleanroom.1", "1.4.8", "1.4.7-cleanroom-alpha.2"})
    void rejectsUnverifiedVersions(String version) {
        assertFalse(ZstdNetServerCompatibility.isSupportedVersion(version));
    }
}
