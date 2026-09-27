package io.github.therealkamisama.miguelnetwork.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeWstunnelTest {
    private static final byte[] EXECUTABLE = "verified-wstunnel".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path gameDirectory;

    @Test
    void bundledDistributionExtractsWithoutNetworkAccess() throws Exception {
        byte[] archive = tarGz(List.of(entry("wstunnel.exe", EXECUTABLE, '0')));
        WstunnelManifest manifest = manifest(archive, EXECUTABLE);
        AtomicInteger downloads = new AtomicInteger();

        Path result = NativeWstunnel.resolve(gameDirectory, null, OperatingSystem.WINDOWS_X86_64, true, manifest,
                ignored -> new ByteArrayInputStream(EXECUTABLE), (uri, target) -> downloads.incrementAndGet());

        assertEquals(0, downloads.get());
        assertEquals(EXECUTABLE.length, Files.size(result));
        assertEquals(result, NativeWstunnel.resolve(gameDirectory, null, OperatingSystem.WINDOWS_X86_64, true,
                manifest, curseForgeResources(), (uri, target) -> downloads.incrementAndGet()));
        assertEquals(0, downloads.get());
    }

    @Test
    void optInDisabledMakesNoNetworkRequest() throws Exception {
        byte[] archive = tarGz(List.of(entry("wstunnel.exe", EXECUTABLE, '0')));
        AtomicInteger downloads = new AtomicInteger();

        IOException failure = assertThrows(IOException.class, () -> NativeWstunnel.resolve(
                gameDirectory, null, OperatingSystem.WINDOWS_X86_64, false, manifest(archive, EXECUTABLE),
                curseForgeResources(), (uri, target) -> downloads.incrementAndGet()));

        assertEquals(0, downloads.get());
        assertTrue(failure.getMessage().contains("config/miguelnetwork-common.toml"));
        assertTrue(failure.getMessage().contains("[wstunnelDownload] enable = true"));
        assertNoTemporaryOrExecutableFiles();
    }

    @Test
    void validCacheAndExplicitOverrideNeverDownload() throws Exception {
        byte[] archive = tarGz(List.of(entry("wstunnel.exe", EXECUTABLE, '0')));
        WstunnelManifest manifest = manifest(archive, EXECUTABLE);
        Path cache = target(manifest);
        Files.createDirectories(cache.getParent());
        Files.write(cache, EXECUTABLE);
        AtomicInteger downloads = new AtomicInteger();

        assertEquals(cache, NativeWstunnel.resolve(gameDirectory, null, OperatingSystem.WINDOWS_X86_64, true,
                manifest, curseForgeResources(), (uri, file) -> downloads.incrementAndGet()));

        Path override = gameDirectory.resolve("operator-wstunnel.exe");
        Files.write(override, EXECUTABLE);
        assertEquals(override.toAbsolutePath(), NativeWstunnel.resolve(gameDirectory, override,
                OperatingSystem.WINDOWS_X86_64, true, manifest, curseForgeResources(),
                (uri, file) -> downloads.incrementAndGet()));
        assertEquals(0, downloads.get());
    }

    @Test
    void verifiedArchiveInstallsAtomicallyAndUsesOfficialUrl() throws Exception {
        byte[] archive = tarGz(List.of(entry("release/wstunnel.exe", EXECUTABLE, '0')));
        AtomicInteger downloads = new AtomicInteger();

        Path result = NativeWstunnel.resolve(gameDirectory, null, OperatingSystem.WINDOWS_X86_64, true,
                manifest(archive, EXECUTABLE), curseForgeResources(), (uri, file) -> {
                    assertEquals("https://github.com/erebe/wstunnel/releases/download/v10.7.1/test-windows.tar.gz",
                            uri.toString());
                    downloads.incrementAndGet();
                    Files.write(file, archive);
                });

        assertEquals(1, downloads.get());
        assertEquals(hex(EXECUTABLE), NativeWstunnel.sha256(result));
        assertNoTemporaryFiles();
    }

    @Test
    void wrongArchiveHashFailsClosed() throws Exception {
        byte[] archive = tarGz(List.of(entry("wstunnel.exe", EXECUTABLE, '0')));
        WstunnelManifest manifest = manifest(archive, EXECUTABLE, "0".repeat(64), hex(EXECUTABLE));
        assertDownloadFailure(manifest, archive);
    }

    @Test
    void wrongExecutableHashFailsClosed() throws Exception {
        byte[] archive = tarGz(List.of(entry("wstunnel.exe", EXECUTABLE, '0')));
        WstunnelManifest manifest = manifest(archive, EXECUTABLE, hex(archive), "0".repeat(64));
        assertDownloadFailure(manifest, archive);
    }

    @Test
    void malformedDuplicateTraversalAndLinkArchivesFailClosed() throws Exception {
        List<byte[]> archives = List.of(
                "not-gzip".getBytes(StandardCharsets.UTF_8),
                tarGz(List.of(entry("a/wstunnel.exe", EXECUTABLE, '0'),
                        entry("b/wstunnel.exe", EXECUTABLE, '0'))),
                tarGz(List.of(entry("../escape", new byte[0], '0'),
                        entry("wstunnel.exe", EXECUTABLE, '0'))),
                tarGz(List.of(entry("wstunnel.exe", new byte[0], '2')))
        );
        for (byte[] archive : archives) {
            assertDownloadFailure(manifest(archive, EXECUTABLE), archive);
        }
    }

    @Test
    void timeoutAndInterruptedDownloadFailClosed() throws Exception {
        byte[] archive = tarGz(List.of(entry("wstunnel.exe", EXECUTABLE, '0')));
        WstunnelManifest manifest = manifest(archive, EXECUTABLE);
        for (IOException failure : List.of(new IOException("timed out"),
                new InterruptedIOException("interrupted"))) {
            assertThrows(IOException.class, () -> NativeWstunnel.resolve(gameDirectory, null,
                    OperatingSystem.WINDOWS_X86_64, true, manifest, curseForgeResources(),
                    (uri, target) -> {
                        Files.writeString(target, "partial");
                        throw failure;
                    }));
            assertNoTemporaryOrExecutableFiles();
        }
    }

    @Test
    void concurrentResolutionDownloadsOnceAndSharesVerifiedCache() throws Exception {
        byte[] archive = tarGz(List.of(entry("wstunnel.exe", EXECUTABLE, '0')));
        WstunnelManifest manifest = manifest(archive, EXECUTABLE);
        AtomicInteger downloads = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        NativeWstunnel.DownloadSource source = (uri, target) -> {
            downloads.incrementAndGet();
            entered.countDown();
            Files.write(target, archive);
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> NativeWstunnel.resolve(gameDirectory, null,
                    OperatingSystem.WINDOWS_X86_64, true, manifest, curseForgeResources(), source));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> NativeWstunnel.resolve(gameDirectory, null,
                    OperatingSystem.WINDOWS_X86_64, true, manifest, curseForgeResources(), source));
            assertEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }
        assertEquals(1, downloads.get());
    }

    @Test
    void unsupportedPlatformIsRejected() {
        synchronized (System.getProperties()) {
            String originalOs = System.getProperty("os.name");
            String originalArch = System.getProperty("os.arch");
            try {
                System.setProperty("os.name", "Linux");
                System.setProperty("os.arch", "aarch64");
                assertThrows(IllegalStateException.class, OperatingSystem::current);
            } finally {
                restoreProperty("os.name", originalOs);
                restoreProperty("os.arch", originalArch);
            }
        }
    }

    private void assertDownloadFailure(WstunnelManifest manifest, byte[] archive) throws Exception {
        assertThrows(IOException.class, () -> NativeWstunnel.resolve(gameDirectory, null,
                OperatingSystem.WINDOWS_X86_64, true, manifest, curseForgeResources(),
                (uri, target) -> Files.write(target, archive)));
        assertNoTemporaryOrExecutableFiles();
    }

    private void assertNoTemporaryOrExecutableFiles() throws IOException {
        Path runtime = gameDirectory.resolve("config/miguelnetwork/runtime");
        if (Files.exists(runtime)) {
            try (var paths = Files.walk(runtime)) {
                assertFalse(paths.anyMatch(Files::isRegularFile));
            }
        }
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (var paths = Files.walk(gameDirectory)) {
            assertFalse(paths.filter(Files::isRegularFile)
                    .anyMatch(path -> path.getFileName().toString().contains(".download")
                            || path.getFileName().toString().contains(".extract")));
        }
    }

    private Path target(WstunnelManifest manifest) {
        return gameDirectory.resolve("config/miguelnetwork/runtime").resolve(manifest.version())
                .resolve("windows-x86_64/wstunnel.exe").toAbsolutePath();
    }

    private static WstunnelManifest manifest(byte[] archive, byte[] executable) throws IOException {
        return manifest(archive, executable, hex(archive), hex(executable));
    }

    private static NativeWstunnel.ResourceSource curseForgeResources() {
        return name -> DistributionDescriptor.RESOURCE.equals(name)
                ? new ByteArrayInputStream(
                        "{\"channel\":\"curseforge\",\"bundledWstunnel\":false}"
                                .getBytes(StandardCharsets.UTF_8))
                : null;
    }

    private static WstunnelManifest manifest(byte[] archive, byte[] executable,
                                              String archiveHash, String executableHash) throws IOException {
        String json = """
                {"version":"10.7.1","upstream":"https://github.com/erebe/wstunnel","platforms":{
                  "windows-x86_64":{"archive":"test-windows.tar.gz","archiveSha256":"%s","binarySha256":"%s"},
                  "linux-x86_64":{"archive":"test-linux.tar.gz","archiveSha256":"%s","binarySha256":"%s"}
                }}
                """.formatted(archiveHash, executableHash, archiveHash, executableHash);
        return WstunnelManifest.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] tarGz(List<TarEntry> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            for (TarEntry entry : entries) {
                byte[] header = new byte[512];
                putAscii(header, 0, 100, entry.name());
                putOctal(header, 100, 8, 0644);
                putOctal(header, 108, 8, 0);
                putOctal(header, 116, 8, 0);
                putOctal(header, 124, 12, entry.data().length);
                putOctal(header, 136, 12, 0);
                for (int index = 148; index < 156; index++) {
                    header[index] = ' ';
                }
                header[156] = (byte) entry.type();
                putAscii(header, 257, 6, "ustar");
                long checksum = 0;
                for (byte value : header) {
                    checksum += Byte.toUnsignedInt(value);
                }
                putAscii(header, 148, 8, String.format("%06o\0 ", checksum));
                gzip.write(header);
                gzip.write(entry.data());
                gzip.write(new byte[(int) ((512 - entry.data().length % 512) % 512)]);
            }
            gzip.write(new byte[1024]);
        }
        return bytes.toByteArray();
    }

    private static TarEntry entry(String name, byte[] data, char type) {
        return new TarEntry(name, data, type);
    }

    private static void putAscii(byte[] target, int offset, int length, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, target, offset, Math.min(bytes.length, length));
    }

    private static void putOctal(byte[] target, int offset, int length, long value) {
        putAscii(target, offset, length, String.format("%0" + (length - 1) + "o\0", value));
    }

    private static String hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private record TarEntry(String name, byte[] data, char type) {
    }
}
