package io.github.therealkamisama.miguelnetwork.core;

import io.github.therealkamisama.miguelnetwork.config.WstunnelDownloadConfig;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

public final class NativeWstunnel {
    static final long MAX_ARCHIVE_BYTES = 100L * 1024L * 1024L;
    private static final System.Logger LOGGER = System.getLogger(NativeWstunnel.class.getName());
    private static final AtomicBoolean OPT_IN_INSTRUCTION_LOGGED = new AtomicBoolean();

    private NativeWstunnel() {
    }

    public static synchronized Path resolve(Path gameDirectory) throws IOException {
        WstunnelManifest manifest = WstunnelManifest.load();
        Path override = configuredOverride();
        OperatingSystem platform;
        try {
            platform = OperatingSystem.current();
        } catch (IllegalStateException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
        return resolve(gameDirectory, override, platform, WstunnelDownloadConfig.enabled(), manifest,
                NativeWstunnel.class::getResourceAsStream, NativeWstunnel::download);
    }

    static synchronized Path resolve(
            Path gameDirectory,
            Path override,
            OperatingSystem platform,
            boolean downloadEnabled,
            WstunnelManifest manifest,
            ResourceSource resources,
            DownloadSource downloads
    ) throws IOException {
        WstunnelManifest.Platform metadata = manifest.platform(platform);
        if (override != null) {
            Path candidate = override.toAbsolutePath().normalize();
            if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Configured wstunnel executable does not exist: " + candidate);
            }
            requireHash(candidate, metadata.binarySha256(), "Configured wstunnel executable");
            ensureExecutable(candidate, platform);
            return candidate;
        }

        Path target = gameDirectory.resolve("config/miguelnetwork/runtime")
                .resolve(manifest.version())
                .resolve(platform.resourceDirectory())
                .resolve(platform.executableName())
                .toAbsolutePath().normalize();

        if (isVerified(target, metadata.binarySha256())) {
            ensureExecutable(target, platform);
            return target;
        }
        Files.deleteIfExists(target);

        String resource = "/native/" + platform.resourceDirectory() + "/" + platform.executableName();
        try (InputStream bundled = resources.open(resource)) {
            if (bundled != null) {
                return installBundled(bundled, target, metadata.binarySha256(), platform);
            }
        }

        try (InputStream descriptorInput = resources.open(DistributionDescriptor.RESOURCE)) {
            if (descriptorInput == null) {
                throw new IOException("Bundled wstunnel resource is missing from a distribution that is not "
                        + "authorized for runtime acquisition: " + resource);
            }
            DistributionDescriptor descriptor = DistributionDescriptor.parse(descriptorInput);
            if (!"curseforge".equals(descriptor.channel()) || descriptor.bundledWstunnel()) {
                throw new IOException("Distribution descriptor does not authorize runtime wstunnel acquisition");
            }
        }

        if (!downloadEnabled) {
            String instruction = "MiguelNetwork wstunnel is not bundled and runtime download is disabled; "
                    + "set [wstunnelDownload] enable = true in config/miguelnetwork-common.toml "
                    + "or supply -Dmiguelnetwork.wstunnel.path=<path>. Transport remains inactive.";
            if (OPT_IN_INSTRUCTION_LOGGED.compareAndSet(false, true)) {
                LOGGER.log(System.Logger.Level.ERROR, instruction);
            }
            throw new IOException(instruction);
        }

        return downloadAndInstall(target, platform, manifest, metadata, downloads);
    }

    private static Path configuredOverride() {
        String value = System.getProperty("miguelnetwork.wstunnel.path", "").trim();
        return value.isEmpty() ? null : Path.of(value);
    }

    private static Path installBundled(InputStream input, Path target, String expectedHash,
                                       OperatingSystem platform) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".install");
        try {
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            requireHash(temporary, expectedHash, "Bundled wstunnel");
            atomicInstall(temporary, target);
            ensureExecutable(target, platform);
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path downloadAndInstall(
            Path target,
            OperatingSystem platform,
            WstunnelManifest manifest,
            WstunnelManifest.Platform metadata,
            DownloadSource downloads
    ) throws IOException {
        Files.createDirectories(target.getParent());
        Path archive = Files.createTempFile(target.getParent(), metadata.archive() + ".", ".download");
        Path executable = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".extract");
        try {
            downloads.download(manifest.downloadUri(platform), archive);
            if (Files.size(archive) > MAX_ARCHIVE_BYTES) {
                throw new IOException("Downloaded wstunnel archive exceeds " + MAX_ARCHIVE_BYTES + " bytes");
            }
            requireHash(archive, metadata.archiveSha256(), "Downloaded wstunnel archive");
            extractExpectedExecutable(archive, executable, platform.executableName());
            requireHash(executable, metadata.binarySha256(), "Downloaded wstunnel executable");
            atomicInstall(executable, target);
            ensureExecutable(target, platform);
            return target;
        } finally {
            Files.deleteIfExists(archive);
            Files.deleteIfExists(executable);
        }
    }

    private static void download(URI uri, Path target) throws IOException {
        if (!"https".equals(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())
                || !uri.getPath().startsWith("/erebe/wstunnel/releases/download/")) {
            throw new IOException("Refusing unapproved wstunnel download URL: " + uri);
        }
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(120_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "MiguelNetwork wstunnel acquisition");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("wstunnel download failed with HTTP " + status + " from " + uri);
            }
            long declaredLength = connection.getContentLengthLong();
            if (declaredLength > MAX_ARCHIVE_BYTES) {
                throw new IOException("wstunnel archive Content-Length exceeds " + MAX_ARCHIVE_BYTES + " bytes");
            }
            try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedIOException("wstunnel download was interrupted");
                    }
                    total += read;
                    if (total > MAX_ARCHIVE_BYTES) {
                        throw new IOException("wstunnel archive exceeds " + MAX_ARCHIVE_BYTES + " bytes");
                    }
                    output.write(buffer, 0, read);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    static void extractExpectedExecutable(Path archive, Path output, String executableName) throws IOException {
        int candidates = 0;
        try (InputStream fileInput = Files.newInputStream(archive);
             InputStream gzip = new GZIPInputStream(new BufferedInputStream(fileInput))) {
            byte[] header = new byte[512];
            while (true) {
                int headerBytes = readBlock(gzip, header);
                if (headerBytes == 0) {
                    break;
                }
                if (headerBytes != header.length) {
                    throw new IOException("Malformed wstunnel tar archive: truncated header");
                }
                if (allZero(header)) {
                    break;
                }
                verifyTarChecksum(header);
                String name = tarString(header, 0, 100);
                String prefix = tarString(header, 345, 155);
                String fullName = prefix.isEmpty() ? name : prefix + "/" + name;
                validateArchivePath(fullName);
                long size = tarOctal(header, 124, 12);
                byte type = header[156];
                if (type == 0 || type == '0') {
                    boolean candidate = fileName(fullName).equals(executableName);
                    if (candidate && ++candidates > 1) {
                        throw new IOException("wstunnel archive contains duplicate " + executableName + " entries");
                    }
                    copyTarEntry(gzip, candidate ? output : null, size);
                } else if (type == '5') {
                    if (size != 0) {
                        throw new IOException("Malformed wstunnel tar directory entry: " + fullName);
                    }
                } else if (type == '1' || type == '2') {
                    throw new IOException("wstunnel archive contains a link entry: " + fullName);
                } else {
                    throw new IOException("wstunnel archive contains unexpected entry type " + (char) type + ": " + fullName);
                }
                skipExactly(gzip, padding(size));
            }
        } catch (EOFException exception) {
            throw new IOException("Malformed wstunnel tar archive: truncated entry", exception);
        }
        if (candidates != 1) {
            throw new IOException("Expected exactly one " + executableName + " in wstunnel archive, found " + candidates);
        }
    }

    private static void copyTarEntry(InputStream input, Path output, long size) throws IOException {
        if (size < 0 || size > MAX_ARCHIVE_BYTES) {
            throw new IOException("Invalid wstunnel tar entry size: " + size);
        }
        OutputStream destination = output == null ? OutputStream.nullOutputStream() : Files.newOutputStream(output);
        try (destination) {
            byte[] buffer = new byte[64 * 1024];
            long remaining = size;
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    throw new EOFException();
                }
                destination.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    private static int readBlock(InputStream input, byte[] block) throws IOException {
        int offset = 0;
        while (offset < block.length) {
            int read = input.read(block, offset, block.length - offset);
            if (read < 0) {
                return offset;
            }
            offset += read;
        }
        return offset;
    }

    private static void verifyTarChecksum(byte[] header) throws IOException {
        long expected = tarOctal(header, 148, 8);
        long actual = 0;
        for (int index = 0; index < header.length; index++) {
            actual += index >= 148 && index < 156 ? 32 : Byte.toUnsignedInt(header[index]);
        }
        if (actual != expected) {
            throw new IOException("Malformed wstunnel tar archive: header checksum mismatch");
        }
    }

    private static long tarOctal(byte[] bytes, int offset, int length) throws IOException {
        String value = tarString(bytes, offset, length).trim();
        if (value.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(value, 8);
        } catch (NumberFormatException exception) {
            throw new IOException("Malformed wstunnel tar archive: invalid octal value", exception);
        }
    }

    private static String tarString(byte[] bytes, int offset, int length) {
        int end = offset;
        while (end < offset + length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.US_ASCII);
    }

    private static void validateArchivePath(String name) throws IOException {
        if (name.isBlank() || name.startsWith("/") || name.startsWith("\\") || name.contains("\\")
                || name.matches("^[A-Za-z]:.*")) {
            throw new IOException("Unsafe path in wstunnel archive: " + name);
        }
        for (String segment : name.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                throw new IOException("Unsafe path in wstunnel archive: " + name);
            }
        }
    }

    private static String fileName(String path) {
        int separator = path.lastIndexOf('/');
        return separator < 0 ? path : path.substring(separator + 1);
    }

    private static long padding(long size) {
        return (512 - (size % 512)) % 512;
    }

    private static void skipExactly(InputStream input, long count) throws IOException {
        long remaining = count;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                if (input.read() < 0) {
                    throw new EOFException();
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private static boolean allZero(byte[] bytes) {
        return Arrays.equals(bytes, new byte[bytes.length]);
    }

    private static boolean isVerified(Path path, String expectedHash) throws IOException {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && expectedHash.equals(sha256(path));
    }

    private static void requireHash(Path path, String expectedHash, String description) throws IOException {
        String actualHash = sha256(path);
        if (!expectedHash.equals(actualHash)) {
            throw new IOException(description + " failed SHA-256 validation. Expected "
                    + expectedHash + ", got " + actualHash);
        }
    }

    private static void atomicInstall(Path temporary, Path target) throws IOException {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void ensureExecutable(Path path, OperatingSystem platform) throws IOException {
        if (platform != OperatingSystem.LINUX_X86_64) {
            return;
        }
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException exception) {
            if (!path.toFile().setExecutable(true, true)) {
                throw new IOException("Cannot mark wstunnel executable: " + path, exception);
            }
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

    @FunctionalInterface
    interface ResourceSource {
        InputStream open(String name) throws IOException;
    }

    @FunctionalInterface
    interface DownloadSource {
        void download(URI uri, Path target) throws IOException;
    }
}
