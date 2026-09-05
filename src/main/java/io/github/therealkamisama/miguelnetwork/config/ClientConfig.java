package io.github.therealkamisama.miguelnetwork.config;

import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import io.github.therealkamisama.miguelnetwork.core.TransportProtocol;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Locale;
import java.util.Optional;

public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.ConfigValue<String> PATH_PREFIX;
    private static final ModConfigSpec.IntValue MAX_TUNNEL_PROCESSES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("MiguelNetwork client-side WebSocket transport settings.");
        ENABLED = builder.comment(
                        "Master client switch. When enabled, endpoints are discovered in WSS, WS, then TCP order.")
                .define("enabled", true);
        PATH_PREFIX = builder.comment("HTTP Upgrade path prefix shared with the server configuration.")
                .define("pathPrefix", MiguelNetworkProtocol.DEFAULT_PATH_PREFIX, ClientConfig::isNonBlankString);
        MAX_TUNNEL_PROCESSES = builder.comment(
                        "Maximum simultaneous per-endpoint wstunnel processes. Least-recently-used entries are evicted.")
                .defineInRange("maxTunnelProcesses", 8, 1, 32);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    public static boolean enabled() {
        return booleanProperty("miguelnetwork.client.enabled", ENABLED.get());
    }

    public static int targetPort() {
        return Integer.getInteger("miguelnetwork.target.port", MiguelNetworkProtocol.MINECRAFT_TARGET_PORT);
    }

    public static String pathPrefix() {
        return System.getProperty("miguelnetwork.pathPrefix", PATH_PREFIX.get());
    }

    public static boolean verifyCertificate() {
        return booleanProperty("miguelnetwork.tls.verify", true);
    }

    public static Optional<TransportProtocol> forcedTransport() {
        String value = System.getProperty("miguelnetwork.client.transport");
        if (value == null || value.isBlank() || value.equalsIgnoreCase("AUTO")) {
            return Optional.empty();
        }
        return Optional.of(TransportProtocol.valueOf(value.trim().toUpperCase(Locale.ROOT)));
    }

    public static int maxTunnelProcesses() {
        int configured = Integer.getInteger("miguelnetwork.client.maxTunnelProcesses", MAX_TUNNEL_PROCESSES.get());
        return Math.max(1, Math.min(32, configured));
    }

    private static boolean booleanProperty(String name, boolean fallback) {
        String value = System.getProperty(name);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static boolean isNonBlankString(Object value) {
        return value instanceof String text && !text.trim().isEmpty();
    }
}
