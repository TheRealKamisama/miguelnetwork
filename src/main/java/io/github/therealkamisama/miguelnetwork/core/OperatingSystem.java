package io.github.therealkamisama.miguelnetwork.core;

import java.util.Locale;

public enum OperatingSystem {
    WINDOWS_X86_64("windows-x86_64", "wstunnel.exe"),
    LINUX_X86_64("linux-x86_64", "wstunnel");

    private final String resourceDirectory;
    private final String executableName;

    OperatingSystem(String resourceDirectory, String executableName) {
        this.resourceDirectory = resourceDirectory;
        this.executableName = executableName;
    }

    public String resourceDirectory() {
        return resourceDirectory;
    }

    public String executableName() {
        return executableName;
    }

    public static OperatingSystem current() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
        if (!x64) {
            throw new IllegalStateException("MiguelNetwork Phase 0 only supports x86_64, found: " + arch);
        }
        if (os.contains("win")) {
            return WINDOWS_X86_64;
        }
        if (os.contains("linux")) {
            return LINUX_X86_64;
        }
        throw new IllegalStateException("MiguelNetwork Phase 0 does not support OS: " + os);
    }
}

