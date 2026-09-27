package io.github.therealkamisama.miguelnetwork.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public record DistributionDescriptor(String channel, boolean bundledWstunnel) {
    public static final String RESOURCE = "/META-INF/miguelnetwork/distribution.json";

    public static DistributionDescriptor parse(InputStream input) throws IOException {
        try {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("channel") || !root.has("bundledWstunnel")) {
                throw new IOException("Distribution descriptor is missing required fields");
            }
            String channel = root.get("channel").getAsString();
            if (channel.isBlank()) {
                throw new IOException("Distribution descriptor channel is blank");
            }
            return new DistributionDescriptor(channel, root.get("bundledWstunnel").getAsBoolean());
        } catch (RuntimeException exception) {
            throw new IOException("Invalid distribution descriptor", exception);
        }
    }
}
