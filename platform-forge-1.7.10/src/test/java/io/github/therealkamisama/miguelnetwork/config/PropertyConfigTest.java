package io.github.therealkamisama.miguelnetwork.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PropertyConfigTest {
    @TempDir Path directory;

    @Test void loadsDottedKeysAndPreservesExistingUserFile() throws Exception {
        var builder = new PropertyConfig.Builder();
        builder.push("discovery");
        var signing = builder.define("signResponses", false);
        var port = builder.defineInRange("port", 25568, 1, 65535);
        var config = builder.build();
        Path file = directory.resolve("test.properties");
        String contents = "# user comment\ndiscovery.signResponses=true\ndiscovery.port=35549\nunknown=preserved\n";
        Files.writeString(file, contents);
        config.load(file);
        assertTrue(signing.get());
        assertEquals(35549, port.get());
        assertEquals(contents, Files.readString(file));
    }

    @Test void invalidSettingsFailAtomically() throws Exception {
        var builder = new PropertyConfig.Builder();
        var enabled = builder.define("enabled", true);
        var port = builder.defineInRange("port", 35548, 1, 65535);
        var config = builder.build();
        Path file = directory.resolve("test.properties");
        Files.writeString(file, "enabled=false\nport=70000\n");
        assertThrows(java.io.IOException.class, () -> config.load(file));
        assertTrue(enabled.get());
        assertEquals(35548, port.get());
    }
}
