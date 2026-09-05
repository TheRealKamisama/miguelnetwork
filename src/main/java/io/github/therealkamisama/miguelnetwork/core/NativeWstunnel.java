package io.github.therealkamisama.miguelnetwork.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class NativeWstunnel {
    public static final String VERSION = "10.7.1";

    private NativeWstunnel() {
    }

    public static Path resolve(Path gameDirectory) throws IOException {
        String override = System.getProperty("miguelnetwork.wstunnel.path", "").trim();
        if (!override.isEmpty()) {
            Path candidate = Path.of(override).toAbsolutePath().normalize();
            if (!Files.isRegularFile(candidate)) {
                throw new IOException("Configured wstunnel executable does not exist: " + candidate);
            }
            return candidate;
        }

        OperatingSystem platform = OperatingSystem.current();
        String resource = "/native/" + platform.resourceDirectory() + "/" + platform.executableName();
        String expectedHash = hashFor(platform);
        Path target = gameDirectory.resolve("config/miguelnetwork/runtime")
                .resolve(VERSION)
                .resolve(platform.resourceDirectory())
                .resolve(platform.executableName())
                .toAbsolutePath().normalize();

        if (Files.isRegularFile(target) && expectedHash.equals(sha256(target))) {
            ensureExecutable(target);
            return target;
        }

        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (InputStream input = NativeWstunnel.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Bundled wstunnel resource is missing: " + resource);
            }
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
        }

        String actualHash = sha256(temporary);
        if (!expectedHash.equals(actualHash)) {
            Files.deleteIfExists(temporary);
            throw new IOException("Bundled wstunnel failed SHA-256 validation. Expected "
                    + expectedHash + ", got " + actualHash);
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        ensureExecutable(target);
        return target;
    }

    private static String hashFor(OperatingSystem platform) {
        switch (platform) {
            case WINDOWS_X86_64:
                return "42fddace83154e7a96205a9f31af5221dbfc442dc56bcf70230ba55fe4af2f2a";
            case LINUX_X86_64:
                return "628143a837e35e8dcbda032bc1e2b07937412bc69e2344b9b583c3acfef8431d";
            default:
                throw new IllegalStateException("Unsupported platform " + platform);
        }
    }

    private static void ensureExecutable(Path path) throws IOException {
        if (OperatingSystem.current() == OperatingSystem.LINUX_X86_64 && !path.toFile().setExecutable(true, true)) {
            throw new IOException("Cannot mark wstunnel executable: " + path);
        }
    }

    public static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
