package io.github.therealkamisama.miguelnetwork.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryCodec;
import io.github.therealkamisama.miguelnetwork.discovery.SignedDiscoveryDocument;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientTrustStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, TrustEntry>>() { }.getType();
    private final Path path;
    private final Map<String, TrustEntry> entries;

    private ClientTrustStore(Path path, Map<String, TrustEntry> entries) {
        this.path = path;
        this.entries = entries;
    }

    static ClientTrustStore open(Path path) {
        if (!Files.isRegularFile(path)) {
            return new ClientTrustStore(path, new LinkedHashMap<>());
        }
        try {
            Map<String, TrustEntry> parsed = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), MAP_TYPE);
            Map<String, TrustEntry> validated = parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
            validated.forEach((endpoint, entry) -> {
                if (endpoint == null || endpoint.isBlank() || entry == null
                        || entry.serverId() == null || entry.publicKey() == null
                        || entry.highestConfigEpoch() < 0) {
                    throw new IllegalArgumentException("Invalid trust-store entry");
                }
            });
            return new ClientTrustStore(path, validated);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read MiguelNetwork trust store " + path, exception);
        }
    }

    synchronized void accept(String endpoint, SignedDiscoveryDocument document) throws GeneralSecurityException {
        String publicKey = DiscoveryCodec.encodePublicKey(document.publicKey());
        var manifest = document.manifest();
        TrustEntry previous = entries.get(endpoint);
        if (previous != null && !previous.publicKey().isBlank()) {
            if (!MessageDigest.isEqual(
                    previous.publicKey().getBytes(StandardCharsets.US_ASCII),
                    publicKey.getBytes(StandardCharsets.US_ASCII))) {
                throw new GeneralSecurityException("Discovery signing key changed for " + endpoint);
            }
            if (!previous.serverId().equals(manifest.serverId())) {
                throw new GeneralSecurityException("Discovery server identity changed for " + endpoint);
            }
            if (manifest.configEpoch() < previous.highestConfigEpoch()) {
                throw new GeneralSecurityException("Discovery configuration rollback for " + endpoint);
            }
        }
        boolean wssFloor = (previous != null && previous.wssFloor())
                || manifest.routes().stream().anyMatch(route -> route.transport().usesTls());
        entries.put(endpoint, new TrustEntry(
                manifest.serverId(), publicKey,
                Math.max(previous == null ? 0 : previous.highestConfigEpoch(), manifest.configEpoch()),
                wssFloor
        ));
        save();
    }

    synchronized void recordWss(String endpoint) {
        TrustEntry previous = entries.get(endpoint);
        if (previous != null && previous.wssFloor()) {
            return;
        }
        entries.put(endpoint, previous == null
                ? new TrustEntry("", "", 0, true)
                : new TrustEntry(previous.serverId(), previous.publicKey(), previous.highestConfigEpoch(), true));
        save();
    }

    synchronized boolean requiresWss(String endpoint) {
        TrustEntry entry = entries.get(endpoint);
        return entry != null && entry.wssFloor();
    }

    private void save() {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(entries, MAP_TYPE), StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist MiguelNetwork trust store " + path, exception);
        }
    }

    private record TrustEntry(String serverId, String publicKey, long highestConfigEpoch, boolean wssFloor) {
    }
}
