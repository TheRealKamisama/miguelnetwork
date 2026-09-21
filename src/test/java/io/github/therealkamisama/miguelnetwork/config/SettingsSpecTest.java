package io.github.therealkamisama.miguelnetwork.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SettingsSpecTest {
    @TempDir Path directory;

    @Test void defaultsKeepUnsignedStandaloneWsAndLegacyFallback() throws Exception {
        Path server = directory.resolve("folder with spaces/miguelnetwork-server.toml");
        Path client = directory.resolve("folder with spaces/miguelnetwork-client.toml");
        ServerConfig.SPEC.load(server);
        ClientConfig.SPEC.load(client);
        assertEquals(DeploymentMode.STANDALONE, ServerConfig.mode());
        assertEquals("WS", ServerConfig.advertisedTransport().name());
        assertFalse(ServerConfig.signDiscoveryResponses());
        assertFalse(ClientConfig.verifyDiscoverySignatures());
        assertFalse(ClientConfig.enforceWssDowngradeProtection());
        assertTrue(ClientConfig.legacyFallback());
        assertTrue(Files.readString(server).contains("[discovery]"));
    }

    @Test void readsExistingFileWithoutDiscardingCommentsOrUnknownKeys() throws Exception {
        Path path = directory.resolve("settings.toml");
        String original = "# keep me\nunknown = 123\n[security]\nverify = true\n";
        Files.writeString(path, original);
        var builder = new SettingsSpec.Builder();
        var count = builder.defineInRange("count", 8, 1, 32);
        builder.push("security");
        var verify = builder.define("verify", false);
        builder.build().load(path);
        assertEquals(8, count.get());
        assertTrue(verify.get());
        assertEquals(original, Files.readString(path));
    }

    @Test void rejectsInvalidValuesWithoutPartiallyPublishingOrRewriting() throws Exception {
        var builder = new SettingsSpec.Builder();
        var enabled = builder.define("enabled", true);
        builder.defineInRange("port", 35548, 1, 65535);
        var spec = builder.build();
        Path path = directory.resolve("settings.toml");
        for (String value : new String[]{"0", "65536", "1.5", "\"123\""}) {
            String text = "enabled = false\nport = " + value + "\n";
            Files.writeString(path, text);
            assertThrows(IllegalStateException.class, () -> spec.load(path));
            assertTrue(enabled.get());
            assertEquals(text, Files.readString(path));
        }
    }

    @Test void rejectsUnknownEnumAndInvalidToml() throws Exception {
        var builder = new SettingsSpec.Builder();
        builder.defineEnum("mode", DeploymentMode.STANDALONE);
        var spec = builder.build();
        Path path = directory.resolve("settings.toml");
        for (String text : new String[]{"mode = \"OTHER\"", "mode = ["}) {
            Files.writeString(path, text);
            assertThrows(IllegalStateException.class, () -> spec.load(path));
            assertEquals(text, Files.readString(path));
        }
    }

    @Test void propertyOverridesLoadedConfiguration() throws Exception {
        Path path = directory.resolve("client.toml");
        Files.writeString(path, "legacyFallback = true\n");
        ClientConfig.SPEC.load(path);
        String key = "miguelnetwork.client.legacyFallback";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "false");
            assertFalse(ClientConfig.legacyFallback());
        } finally {
            if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
        }
    }
}
