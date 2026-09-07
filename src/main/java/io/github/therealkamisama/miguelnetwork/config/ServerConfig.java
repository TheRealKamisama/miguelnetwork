package io.github.therealkamisama.miguelnetwork.config;

import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import io.github.therealkamisama.miguelnetwork.core.TransportProtocol;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.EnumValue<DeploymentMode> MODE;
    private static final ModConfigSpec.ConfigValue<String> BIND_HOST;
    private static final ModConfigSpec.IntValue PUBLIC_PORT;
    private static final ModConfigSpec.ConfigValue<String> PATH_PREFIX;
    private static final ModConfigSpec.BooleanValue DISCOVERY_ENABLED;
    private static final ModConfigSpec.BooleanValue SIGN_DISCOVERY;
    private static final ModConfigSpec.ConfigValue<String> DISCOVERY_BIND_HOST;
    private static final ModConfigSpec.IntValue DISCOVERY_PORT;
    private static final ModConfigSpec.EnumValue<TransportProtocol> ADVERTISED_TRANSPORT;
    private static final ModConfigSpec.ConfigValue<String> ADVERTISED_HOST;
    private static final ModConfigSpec.IntValue ADVERTISED_PORT;
    private static final ModConfigSpec.LongValue DISCOVERY_CONFIG_EPOCH;
    private static final ModConfigSpec.IntValue DISCOVERY_VALIDITY_SECONDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(
                "MiguelNetwork dedicated-server settings.",
                "Minecraft target IP/port come from server.properties; the supported ZstdNet listener is auto-detected."
        );
        ENABLED = builder.comment("Start MiguelNetwork automatically when the dedicated server is ready.")
                .define("enabled", true);
        MODE = builder.comment(
                        "STANDALONE: the Mod multiplexes Discovery and wstunnel on publicPort using plain WS.",
                        "EXTERNAL_PROXY: Nginx or another gateway owns the public endpoint and may provide WSS."
                ).defineEnum("mode", DeploymentMode.STANDALONE);
        BIND_HOST = builder.comment(
                        "STANDALONE: public gateway bind address.",
                        "EXTERNAL_PROXY: private wstunnel bind address reachable by the external gateway."
                ).define("bindHost", "0.0.0.0", ServerConfig::isNonBlankString);
        PUBLIC_PORT = builder.comment(
                        "STANDALONE: the single public WS/Discovery port.",
                        "EXTERNAL_PROXY: the private wstunnel upstream port. It must differ from server-port."
                ).defineInRange("publicPort", 35548, 1, 65535);
        PATH_PREFIX = builder.comment("HTTP Upgrade path prefix shared with clients.")
                .define("pathPrefix", MiguelNetworkProtocol.DEFAULT_PATH_PREFIX, ServerConfig::isNonBlankString);

        builder.push("discovery");
        DISCOVERY_ENABLED = builder.comment("Publish MiguelNetwork Discovery manifests.")
                .define("enabled", true);
        SIGN_DISCOVERY = builder.comment(
                        "Sign Discovery manifests with a persistent Ed25519 identity.",
                        "Optional and disabled by default because the underlying Minecraft stream is normally unencrypted."
                ).define("signResponses", false);
        DISCOVERY_BIND_HOST = builder.comment(
                        "EXTERNAL_PROXY only: private Discovery HTTP bind address. Ignored in STANDALONE mode."
                ).define("bindHost", "127.0.0.1", ServerConfig::isNonBlankString);
        DISCOVERY_PORT = builder.comment(
                        "EXTERNAL_PROXY only: private Discovery HTTP port. Ignored in STANDALONE mode."
                ).defineInRange("port", 25568, 1, 65535);
        ADVERTISED_TRANSPORT = builder.comment(
                        "EXTERNAL_PROXY public transport. Use WSS when the gateway terminates TLS.",
                        "STANDALONE always advertises WS."
                ).defineEnum("advertisedTransport", TransportProtocol.WS);
        ADVERTISED_HOST = builder.comment("Public host. Empty derives it from the request Host header.")
                .define("advertisedHost", "");
        ADVERTISED_PORT = builder.comment("Public port. Zero derives it from the request Host header.")
                .defineInRange("advertisedPort", 0, 0, 65535);
        DISCOVERY_CONFIG_EPOCH = builder.comment(
                        "Monotonic route revision used only when clients enable signature verification."
                ).defineInRange("configEpoch", 1L, 0L, Long.MAX_VALUE);
        DISCOVERY_VALIDITY_SECONDS = builder.comment("Lifetime of each Discovery manifest.")
                .defineInRange("validitySeconds", 120, 15, 3600);
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {
    }

    public static boolean enabled() {
        return booleanProperty("miguelnetwork.server.enabled", ENABLED.get());
    }

    public static DeploymentMode mode() {
        String override = System.getProperty("miguelnetwork.server.mode");
        return override == null ? MODE.get() : DeploymentMode.valueOf(override.trim().toUpperCase());
    }

    public static String bindHost() {
        return System.getProperty("miguelnetwork.bindHost", BIND_HOST.get()).trim();
    }

    public static int publicPort() {
        return Integer.getInteger("miguelnetwork.public.port", PUBLIC_PORT.get());
    }

    public static String pathPrefix() {
        return System.getProperty("miguelnetwork.pathPrefix", PATH_PREFIX.get()).trim();
    }

    public static boolean discoveryEnabled() {
        return booleanProperty("miguelnetwork.discovery.enabled", DISCOVERY_ENABLED.get());
    }

    public static boolean signDiscoveryResponses() {
        return booleanProperty("miguelnetwork.discovery.signResponses", SIGN_DISCOVERY.get());
    }

    public static String discoveryBindHost() {
        return System.getProperty("miguelnetwork.discovery.bindHost", DISCOVERY_BIND_HOST.get()).trim();
    }

    public static int discoveryPort() {
        return Integer.getInteger("miguelnetwork.discovery.port", DISCOVERY_PORT.get());
    }

    public static TransportProtocol advertisedTransport() {
        if (mode() == DeploymentMode.STANDALONE) {
            return TransportProtocol.WS;
        }
        return TransportProtocol.property(
                "miguelnetwork.discovery.advertisedTransport", ADVERTISED_TRANSPORT.get());
    }

    public static String advertisedHost() {
        return System.getProperty("miguelnetwork.discovery.advertisedHost", ADVERTISED_HOST.get()).trim();
    }

    public static int advertisedPort() {
        return Integer.getInteger("miguelnetwork.discovery.advertisedPort", ADVERTISED_PORT.get());
    }

    public static long discoveryConfigEpoch() {
        return Long.getLong("miguelnetwork.discovery.configEpoch", DISCOVERY_CONFIG_EPOCH.get());
    }

    public static int discoveryValiditySeconds() {
        return Integer.getInteger("miguelnetwork.discovery.validitySeconds", DISCOVERY_VALIDITY_SECONDS.get());
    }

    private static boolean booleanProperty(String name, boolean fallback) {
        String value = System.getProperty(name);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static boolean isNonBlankString(Object value) {
        return value instanceof String text && !text.trim().isEmpty();
    }
}
