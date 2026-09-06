package io.github.therealkamisama.miguelnetwork.config;

import io.github.therealkamisama.miguelnetwork.core.TransportProtocol;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.EnumValue<TransportProtocol> TRANSPORT;
    private static final ModConfigSpec.ConfigValue<String> BIND_HOST;
    private static final ModConfigSpec.IntValue PUBLIC_PORT;
    private static final ModConfigSpec.ConfigValue<String> PATH_PREFIX;
    private static final ModConfigSpec.ConfigValue<String> CERTIFICATE;
    private static final ModConfigSpec.ConfigValue<String> PRIVATE_KEY;
    private static final ModConfigSpec.BooleanValue ALLOW_BUILT_IN_SELF_SIGNED;
    private static final ModConfigSpec.BooleanValue DISCOVERY_ENABLED;
    private static final ModConfigSpec.ConfigValue<String> DISCOVERY_BIND_HOST;
    private static final ModConfigSpec.IntValue DISCOVERY_PORT;
    private static final ModConfigSpec.EnumValue<TransportProtocol> ADVERTISED_TRANSPORT;
    private static final ModConfigSpec.ConfigValue<String> ADVERTISED_HOST;
    private static final ModConfigSpec.IntValue ADVERTISED_PORT;
    private static final ModConfigSpec.LongValue DISCOVERY_CONFIG_EPOCH;
    private static final ModConfigSpec.IntValue DISCOVERY_VALIDITY_SECONDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("MiguelNetwork dedicated-server WebSocket transport settings.");
        ENABLED = builder.comment("Start the bundled wstunnel server when the dedicated server is ready.")
                .define("enabled", false);
        TRANSPORT = builder.comment(
                        "WebSocket transport. WSS is the secure default; use WS only behind a TLS reverse proxy",
                        "or for an explicitly isolated development test.")
                .defineEnum("transport", TransportProtocol.WSS);
        BIND_HOST = builder.comment("Address exposed by wstunnel. Use 0.0.0.0 for all IPv4 interfaces.")
                .define("bindHost", "0.0.0.0", ServerConfig::isNonBlankString);
        PUBLIC_PORT = builder.comment("WebSocket listener port.")
                .defineInRange("publicPort", 25565, 1, 65535);
        PATH_PREFIX = builder.comment("HTTP Upgrade path prefix shared with clients.")
                .define("pathPrefix", "miguelnetwork-v1", ServerConfig::isNonBlankString);
        CERTIFICATE = builder.comment("Absolute path to a PEM certificate chain.")
                .define("certificate", "");
        PRIVATE_KEY = builder.comment("Absolute path to the corresponding PEM private key.")
                .define("privateKey", "");
        ALLOW_BUILT_IN_SELF_SIGNED = builder.comment(
                        "Allow wstunnel's built-in self-signed certificate. Development only; never expose publicly.")
                .define("allowBuiltInSelfSigned", false);
        builder.push("discovery");
        DISCOVERY_ENABLED = builder.comment("Serve signed MiguelNetwork Discovery manifests on a loopback HTTP port.")
                .define("enabled", true);
        DISCOVERY_BIND_HOST = builder.comment("Bind address for the HTTP endpoint consumed by a reverse proxy.")
                .define("bindHost", "127.0.0.1", ServerConfig::isNonBlankString);
        DISCOVERY_PORT = builder.comment("Loopback HTTP port for /.well-known/miguelnetwork/v1.")
                .defineInRange("port", 25567, 1, 65535);
        ADVERTISED_TRANSPORT = builder.comment("Public transport after TLS termination (normally WSS).")
                .defineEnum("advertisedTransport", TransportProtocol.WSS);
        ADVERTISED_HOST = builder.comment("Public host. Empty derives it from the reverse-proxy Host header.")
                .define("advertisedHost", "");
        ADVERTISED_PORT = builder.comment("Public port. Zero derives it from the reverse-proxy Host header.")
                .defineInRange("advertisedPort", 0, 0, 65535);
        DISCOVERY_CONFIG_EPOCH = builder.comment("Monotonic number. Increase when intentionally replacing routes.")
                .defineInRange("configEpoch", 1L, 0L, Long.MAX_VALUE);
        DISCOVERY_VALIDITY_SECONDS = builder.comment("Lifetime of each signed manifest.")
                .defineInRange("validitySeconds", 120, 15, 3600);
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {
    }

    public static boolean enabled() {
        return booleanProperty("miguelnetwork.server.enabled", ENABLED.get());
    }

    public static TransportProtocol transport() {
        return TransportProtocol.property("miguelnetwork.server.transport", TRANSPORT.get());
    }

    public static String bindHost() {
        return System.getProperty("miguelnetwork.bindHost", BIND_HOST.get());
    }

    public static int publicPort() {
        return Integer.getInteger("miguelnetwork.public.port", PUBLIC_PORT.get());
    }

    public static int targetPort(int actualServerPort) {
        return Integer.getInteger("miguelnetwork.target.port", actualServerPort);
    }

    public static String pathPrefix() {
        return System.getProperty("miguelnetwork.pathPrefix", PATH_PREFIX.get());
    }

    public static String certificate() {
        return System.getProperty("miguelnetwork.tls.certificate", CERTIFICATE.get()).trim();
    }

    public static String privateKey() {
        return System.getProperty("miguelnetwork.tls.privateKey", PRIVATE_KEY.get()).trim();
    }

    public static boolean allowBuiltInSelfSigned() {
        return booleanProperty("miguelnetwork.tls.allowBuiltInSelfSigned", ALLOW_BUILT_IN_SELF_SIGNED.get());
    }

    public static boolean discoveryEnabled() {
        return booleanProperty("miguelnetwork.discovery.enabled", DISCOVERY_ENABLED.get());
    }

    public static String discoveryBindHost() {
        return System.getProperty("miguelnetwork.discovery.bindHost", DISCOVERY_BIND_HOST.get());
    }

    public static int discoveryPort() {
        return Integer.getInteger("miguelnetwork.discovery.port", DISCOVERY_PORT.get());
    }

    public static TransportProtocol advertisedTransport() {
        return TransportProtocol.property("miguelnetwork.discovery.advertisedTransport", ADVERTISED_TRANSPORT.get());
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
