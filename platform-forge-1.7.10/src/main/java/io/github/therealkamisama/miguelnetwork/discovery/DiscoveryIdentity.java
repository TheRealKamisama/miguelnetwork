package io.github.therealkamisama.miguelnetwork.discovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

public record DiscoveryIdentity(String serverId, PrivateKey privateKey, PublicKey publicKey, String keyId) {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public static DiscoveryIdentity loadOrCreate(Path directory) throws IOException, GeneralSecurityException {
        Files.createDirectories(directory);
        Path identityFile = directory.resolve("discovery-identity.key");
        if (Files.isRegularFile(identityFile)) {
            return read(identityFile);
        }

        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String serverId = UUID.randomUUID().toString();
        String contents = "miguel-discovery-identity-v1\n"
                + serverId + "\n"
                + ENCODER.encodeToString(pair.getPrivate().getEncoded()) + "\n"
                + ENCODER.encodeToString(pair.getPublic().getEncoded()) + "\n";
        Path temporary = directory.resolve("discovery-identity.key.tmp-" + UUID.randomUUID());
        Files.writeString(temporary, contents, StandardCharsets.US_ASCII);
        restrictPermissions(temporary);
        try {
            Files.move(temporary, identityFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, identityFile);
        }
        restrictPermissions(identityFile);
        return new DiscoveryIdentity(serverId, pair.getPrivate(), pair.getPublic(), DiscoveryCodec.keyId(pair.getPublic()));
    }

    private static DiscoveryIdentity read(Path path) throws IOException, GeneralSecurityException {
        String[] lines = Files.readString(path, StandardCharsets.US_ASCII).split("\\R");
        if (lines.length < 4 || !lines[0].equals("miguel-discovery-identity-v1")) {
            throw new IOException("Unsupported MiguelNetwork Discovery identity file: " + path);
        }
        KeyFactory factory = KeyFactory.getInstance("Ed25519");
        PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(DECODER.decode(lines[2])));
        PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(DECODER.decode(lines[3])));
        return new DiscoveryIdentity(lines[1], privateKey, publicKey, DiscoveryCodec.keyId(publicKey));
    }

    private static void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (IOException | UnsupportedOperationException ignored) {
            // Windows ACLs and non-POSIX file systems are managed by the containing game directory permissions.
        }
    }
}
