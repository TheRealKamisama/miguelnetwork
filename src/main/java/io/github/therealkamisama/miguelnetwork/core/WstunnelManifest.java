package io.github.therealkamisama.miguelnetwork.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

final class WstunnelManifest {
    static final String RESOURCE = "/META-INF/miguelnetwork/wstunnel-manifest.json";
    private static final URI OFFICIAL_UPSTREAM = URI.create("https://github.com/erebe/wstunnel");

    private final String version;
    private final URI upstream;
    private final Map<OperatingSystem, Platform> platforms;

    private WstunnelManifest(String version, URI upstream, Map<OperatingSystem, Platform> platforms) {
        this.version = version;
        this.upstream = upstream;
        this.platforms = Map.copyOf(platforms);
    }

    static WstunnelManifest load() throws IOException {
        try (InputStream input = WstunnelManifest.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing pinned wstunnel manifest: " + RESOURCE);
            }
            return parse(input);
        }
    }

    static WstunnelManifest parse(InputStream input) throws IOException {
        try {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            String version = requiredString(root, "version");
            URI upstream = URI.create(requiredString(root, "upstream"));
            if (!OFFICIAL_UPSTREAM.equals(upstream)) {
                throw new IOException("Pinned wstunnel manifest has an unapproved upstream: " + upstream);
            }
            JsonObject platformObject = root.getAsJsonObject("platforms");
            if (platformObject == null) {
                throw new IOException("Pinned wstunnel manifest is missing platforms");
            }
            Map<OperatingSystem, Platform> platforms = new EnumMap<>(OperatingSystem.class);
            for (OperatingSystem operatingSystem : OperatingSystem.values()) {
                JsonObject metadata = platformObject.getAsJsonObject(operatingSystem.resourceDirectory());
                if (metadata == null) {
                    throw new IOException("Pinned wstunnel manifest is missing " + operatingSystem.resourceDirectory());
                }
                String archive = requiredString(metadata, "archive");
                if (archive.contains("/") || archive.contains("\\")) {
                    throw new IOException("Invalid wstunnel archive name: " + archive);
                }
                platforms.put(operatingSystem, new Platform(
                        archive,
                        requiredHash(metadata, "archiveSha256"),
                        requiredHash(metadata, "binarySha256")
                ));
            }
            return new WstunnelManifest(version, upstream, platforms);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid pinned wstunnel manifest", exception);
        }
    }

    String version() {
        return version;
    }

    Platform platform(OperatingSystem operatingSystem) throws IOException {
        Platform platform = platforms.get(operatingSystem);
        if (platform == null) {
            throw new IOException("No pinned wstunnel metadata for " + operatingSystem.resourceDirectory());
        }
        return platform;
    }

    URI downloadUri(OperatingSystem operatingSystem) throws IOException {
        Platform platform = platform(operatingSystem);
        return upstream.resolve("/erebe/wstunnel/releases/download/v" + version + "/" + platform.archive());
    }

    private static String requiredString(JsonObject object, String name) throws IOException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || value.getAsString().isBlank()) {
            throw new IOException("Pinned wstunnel manifest has invalid " + name);
        }
        return value.getAsString();
    }

    private static String requiredHash(JsonObject object, String name) throws IOException {
        String value = requiredString(object, name).toLowerCase();
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IOException("Pinned wstunnel manifest has invalid " + name);
        }
        return value;
    }

    record Platform(String archive, String archiveSha256, String binarySha256) {
    }
}
