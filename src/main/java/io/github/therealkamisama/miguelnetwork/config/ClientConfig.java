package io.github.therealkamisama.miguelnetwork.config;

import io.github.therealkamisama.miguelnetwork.core.EndpointMatcher;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_SERVERS;
    private static final ModConfigSpec.IntValue TARGET_PORT;
    private static final ModConfigSpec.ConfigValue<String> PATH_PREFIX;
    private static final ModConfigSpec.BooleanValue VERIFY_CERTIFICATE;
    private static final ModConfigSpec.IntValue MAX_TUNNEL_PROCESSES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("MiguelNetwork client-side WSS transport settings.");
        ENABLED = builder.comment("Master client switch. An empty allowlist still routes nothing.")
                .define("enabled", false);
        ALLOWED_SERVERS = builder.comment(
                        "Minecraft server addresses that should use WSS.",
                        "Entries may be host, host:port, [IPv6]:port, *.example.com, or * for every server.")
                .defineListAllowEmpty("allowedServers", List.of(), () -> "mc.example.com:25565",
                        ClientConfig::isNonBlankString);
        TARGET_PORT = builder.comment("Internal Minecraft port requested from the server-side wstunnel.")
                .defineInRange("targetPort", 25566, 1, 65535);
        PATH_PREFIX = builder.comment("HTTP Upgrade path prefix shared with the server configuration.")
                .define("pathPrefix", "miguelnetwork-v1", ClientConfig::isNonBlankString);
        VERIFY_CERTIFICATE = builder.comment(
                        "Verify the WSS certificate and hostname. Keep enabled outside isolated development tests.")
                .define("verifyCertificate", true);
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

    public static boolean allows(String host, int port) {
        String override = System.getProperty("miguelnetwork.client.allowedServers");
        List<String> entries = override == null
                ? ALLOWED_SERVERS.get().stream().map(String::valueOf).toList()
                : Arrays.stream(override.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
        return EndpointMatcher.matches(host, port, entries);
    }

    public static int targetPort() {
        return Integer.getInteger("miguelnetwork.target.port", TARGET_PORT.get());
    }

    public static String pathPrefix() {
        return System.getProperty("miguelnetwork.pathPrefix", PATH_PREFIX.get());
    }

    public static boolean verifyCertificate() {
        return booleanProperty("miguelnetwork.tls.verify", VERIFY_CERTIFICATE.get());
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
