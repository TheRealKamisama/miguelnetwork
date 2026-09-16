package io.github.therealkamisama.miguelnetwork.config;

import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import io.github.therealkamisama.miguelnetwork.core.TransportProtocol;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Locale;
import java.util.Optional;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> PATH_PREFIX;
    private static final ForgeConfigSpec.IntValue MAX_TUNNEL_PROCESSES;
    private static final ForgeConfigSpec.BooleanValue LEGACY_FALLBACK;
    private static final ForgeConfigSpec.BooleanValue VERIFY_DISCOVERY_SIGNATURES;
    private static final ForgeConfigSpec.BooleanValue ENFORCE_WSS_DOWNGRADE_PROTECTION;
    private static final ForgeConfigSpec.BooleanValue VERIFY_TLS_CERTIFICATES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("MiguelNetwork client-side WebSocket transport settings.");
        ENABLED = builder.comment(
                        "Master client switch. When enabled, endpoints are discovered in WSS, WS, then TCP order.")
                .define("enabled", true);
        PATH_PREFIX = builder.comment("HTTP Upgrade path prefix shared with the server configuration.")
                .define("pathPrefix", MiguelNetworkProtocol.DEFAULT_PATH_PREFIX, ClientConfig::isNonBlankString);
        MAX_TUNNEL_PROCESSES = builder.comment(
                        "Maximum simultaneous per-endpoint wstunnel processes. Least-recently-used entries are evicted.")
                .defineInRange("maxTunnelProcesses", 8, 1, 32);
        LEGACY_FALLBACK = builder.comment(
                        "Try the legacy WSS/WS/TCP probe when Discovery is unavailable for a new server.")
                .define("legacyFallback", true);
        builder.push("security");
        VERIFY_DISCOVERY_SIGNATURES = builder.comment(
                        "Require Ed25519-signed Discovery documents and pin each server identity.",
                        "Disabled by default; enable only together with discovery.signResponses on the server."
                ).define("verifyDiscoverySignatures", false);
        ENFORCE_WSS_DOWNGRADE_PROTECTION = builder.comment(
                        "After a successful WSS connection, refuse later WS/TCP downgrade for that endpoint.",
                        "Disabled by default so WSS remains an optional deployment choice."
                ).define("enforceWssDowngradeProtection", false);
        VERIFY_TLS_CERTIFICATES = builder.comment(
                        "Verify the normal CA chain and hostname whenever a WSS route is selected."
                ).define("verifyTlsCertificates", true);
        builder.pop();
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
        return booleanProperty("miguelnetwork.tls.verify", VERIFY_TLS_CERTIFICATES.get());
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

    public static boolean legacyFallback() {
        return booleanProperty("miguelnetwork.client.legacyFallback", LEGACY_FALLBACK.get());
    }

    public static boolean verifyDiscoverySignatures() {
        return booleanProperty(
                "miguelnetwork.discovery.verifySignatures", VERIFY_DISCOVERY_SIGNATURES.get());
    }

    public static boolean enforceWssDowngradeProtection() {
        return booleanProperty(
                "miguelnetwork.client.enforceWssDowngradeProtection", ENFORCE_WSS_DOWNGRADE_PROTECTION.get());
    }

    private static boolean booleanProperty(String name, boolean fallback) {
        String value = System.getProperty(name);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static boolean isNonBlankString(Object value) {
        return value instanceof String text && !text.trim().isEmpty();
    }
}
