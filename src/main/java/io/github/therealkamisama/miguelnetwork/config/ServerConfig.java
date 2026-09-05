package io.github.therealkamisama.miguelnetwork.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.ConfigValue<String> BIND_HOST;
    private static final ModConfigSpec.IntValue PUBLIC_PORT;
    private static final ModConfigSpec.IntValue TARGET_PORT;
    private static final ModConfigSpec.ConfigValue<String> PATH_PREFIX;
    private static final ModConfigSpec.ConfigValue<String> CERTIFICATE;
    private static final ModConfigSpec.ConfigValue<String> PRIVATE_KEY;
    private static final ModConfigSpec.BooleanValue ALLOW_BUILT_IN_SELF_SIGNED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("MiguelNetwork dedicated-server WSS transport settings.");
        ENABLED = builder.comment("Start the bundled wstunnel server when the dedicated server is ready.")
                .define("enabled", false);
        BIND_HOST = builder.comment("Address exposed by wstunnel. Use 0.0.0.0 for all IPv4 interfaces.")
                .define("bindHost", "0.0.0.0", ServerConfig::isNonBlankString);
        PUBLIC_PORT = builder.comment("Public WSS listener port.")
                .defineInRange("publicPort", 25565, 1, 65535);
        TARGET_PORT = builder.comment("Internal Minecraft TCP port. Zero uses the actual dedicated-server port.")
                .defineInRange("targetPort", 0, 0, 65535);
        PATH_PREFIX = builder.comment("HTTP Upgrade path prefix shared with clients.")
                .define("pathPrefix", "miguelnetwork-v1", ServerConfig::isNonBlankString);
        CERTIFICATE = builder.comment("Absolute path to a PEM certificate chain.")
                .define("certificate", "");
        PRIVATE_KEY = builder.comment("Absolute path to the corresponding PEM private key.")
                .define("privateKey", "");
        ALLOW_BUILT_IN_SELF_SIGNED = builder.comment(
                        "Allow wstunnel's built-in self-signed certificate. Development only; never expose publicly.")
                .define("allowBuiltInSelfSigned", false);
        SPEC = builder.build();
    }

    private ServerConfig() {
    }

    public static boolean enabled() {
        return booleanProperty("miguelnetwork.server.enabled", ENABLED.get());
    }

    public static String bindHost() {
        return System.getProperty("miguelnetwork.bindHost", BIND_HOST.get());
    }

    public static int publicPort() {
        return Integer.getInteger("miguelnetwork.public.port", PUBLIC_PORT.get());
    }

    public static int targetPort(int actualServerPort) {
        int configured = Integer.getInteger("miguelnetwork.target.port", TARGET_PORT.get());
        return configured == 0 ? actualServerPort : configured;
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

    private static boolean booleanProperty(String name, boolean fallback) {
        String value = System.getProperty(name);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static boolean isNonBlankString(Object value) {
        return value instanceof String text && !text.trim().isEmpty();
    }
}
