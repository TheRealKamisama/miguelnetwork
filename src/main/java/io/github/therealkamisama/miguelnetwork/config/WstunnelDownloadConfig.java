package io.github.therealkamisama.miguelnetwork.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class WstunnelDownloadConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment(
                "External wstunnel acquisition settings.",
                "This opt-in is consulted only when no bundled or operator-supplied executable is available."
        );
        builder.push("wstunnelDownload");
        ENABLE = builder.comment(
                        "Allow MiguelNetwork to download the pinned wstunnel archive from the official GitHub Release.",
                        "The archive and executable are SHA-256 verified before installation and execution."
                )
                .define("enable", false);
        builder.pop();
        SPEC = builder.build();
    }

    private WstunnelDownloadConfig() {
    }

    public static boolean enabled() {
        String override = System.getProperty("miguelnetwork.wstunnel.download.enabled");
        return override == null ? ENABLE.get() : Boolean.parseBoolean(override);
    }
}
