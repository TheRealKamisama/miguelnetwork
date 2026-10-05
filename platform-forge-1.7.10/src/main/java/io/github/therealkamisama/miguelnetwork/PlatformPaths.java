package io.github.therealkamisama.miguelnetwork;

import java.nio.file.Path;

public final class PlatformPaths {
    private static volatile Path gameDirectory = Path.of(".").toAbsolutePath().normalize();

    private PlatformPaths() {}

    public static Path gameDirectory() { return gameDirectory; }

    static void initialize(Path configDirectory) {
        gameDirectory = configDirectory.toAbsolutePath().normalize().getParent();
    }
}
