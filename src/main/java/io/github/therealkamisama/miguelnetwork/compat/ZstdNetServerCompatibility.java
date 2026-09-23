package io.github.therealkamisama.miguelnetwork.compat;

import io.github.therealkamisama.miguelnetwork.MiguelNetwork;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Properties;

/**
 * The server-side part of the optional ZstdNet Cleanroom adapter.
 *
 * <p>ZstdNet deliberately has no compile-time integration API. Keep this class
 * reflective so MiguelNetwork remains useful when ZstdNet is absent and does
 * not bundle or reimplement its compression stack.</p>
 */
public final class ZstdNetServerCompatibility {
    public static final String SUPPORTED_VERSION = "1.4.7-cleanroom-alpha.1";

    private ZstdNetServerCompatibility() {
    }

    public static OptionalInt discoverListenPort() {
        return discoverEndpoints(0).map(DetectedEndpoints::zstdNetPort)
                .map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    /** Discovers the live listener and the backend selected by ZstdNet's dedicated-server takeover. */
    public static Optional<DetectedEndpoints> discoverEndpoints(int minecraftTargetFallback) {
        Optional<String> version = installedVersion();
        if (version.isEmpty()) {
            return Optional.empty();
        }
        if (!isSupportedVersion(version.get())) {
            MiguelNetwork.LOGGER.warn(
                    "ZstdNet {} is installed, but MiguelNetwork only enables its reflective adapter for {}",
                    version.get(), SUPPORTED_VERSION
            );
            return Optional.empty();
        }
        try {
            Class<?> config = Class.forName("cn.tohsaka.factory.zstdnet.server.ServerProxyConfigFile");
            if (!configuredEnabled(config)) {
                MiguelNetwork.LOGGER.info("MiguelNetwork found ZstdNet {}, but its server proxy is disabled",
                        version.get());
                return Optional.empty();
            }
            Method readListenPort = config.getMethod("readListenPort");
            int configuredListenPort = ((Number) readListenPort.invoke(null)).intValue();
            int listenPort = resolveRuntimeListenPort(configuredListenPort);
            int minecraftTargetPort = minecraftTargetFallback;

            Object autoTakeoverPlan = activeAutoTakeoverPlan();
            if (autoTakeoverPlan != null) {
                listenPort = recordPort(autoTakeoverPlan, "listenPort");
                minecraftTargetPort = recordPort(autoTakeoverPlan, "targetPort");
            }

            validatePort("listen", listenPort);
            if (minecraftTargetPort != 0) {
                validatePort("Minecraft target", minecraftTargetPort);
            }
            MiguelNetwork.LOGGER.info(
                    "MiguelNetwork detected supported ZstdNet {} listener port {} and Minecraft target port {}",
                    version.get(), listenPort, minecraftTargetPort);
            return Optional.of(new DetectedEndpoints(minecraftTargetPort, listenPort));
        } catch (ReflectiveOperationException | IOException | RuntimeException exception) {
            MiguelNetwork.LOGGER.warn(
                    "Cannot inspect the installed ZstdNet server; compatibility route is disabled", exception);
            return Optional.empty();
        }
    }

    public static boolean isSupportedVersion(String version) {
        return SUPPORTED_VERSION.equals(version);
    }

    /** Loader-neutral version gate used by common code before entering client-only integration. */
    public static boolean isSupportedInstalled() {
        return installedVersion().filter(ZstdNetServerCompatibility::isSupportedVersion).isPresent();
    }

    private static boolean configuredEnabled(Class<?> config) throws ReflectiveOperationException, IOException {
        Path path = (Path) config.getMethod("path").invoke(null);
        if (!Files.isRegularFile(path)) {
            // ZstdNet creates its default enabled configuration during startup.
            return true;
        }
        Properties properties = new Properties();
        String text = Files.readString(path, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        try (Reader reader = new StringReader(text)) {
            properties.load(reader);
        }
        return Boolean.parseBoolean(properties.getProperty("enabled", "false").trim());
    }

    private static int resolveRuntimeListenPort(int fallback) {
        try {
            Class<?> bootstrap = Class.forName("cn.tohsaka.factory.zstdnet.server.ServerProxyBootstrap");
            boolean running = (Boolean) bootstrap.getMethod("running").invoke(null);
            if (!running) {
                return fallback;
            }
            return ((Number) bootstrap.getMethod("listenPort", int.class).invoke(null, fallback)).intValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    private static Object activeAutoTakeoverPlan() {
        try {
            Class<?> autoPort = Class.forName("cn.tohsaka.factory.zstdnet.server.DedicatedServerAutoPort");
            return autoPort.getMethod("activePlan").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static int recordPort(Object record, String component) throws ReflectiveOperationException {
        return ((Number) record.getClass().getMethod(component).invoke(record)).intValue();
    }

    private static void validatePort(String name, int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("ZstdNet returned invalid " + name + " port " + port);
        }
    }

    public record DetectedEndpoints(int minecraftTargetPort, int zstdNetPort) {
    }

    static Optional<String> installedVersion() {
        try {
            Class<?> loaderClass = Class.forName("net.minecraftforge.fml.common.Loader");
            Object loader = loaderClass.getMethod("instance").invoke(null);
            Object indexed = loaderClass.getMethod("getIndexedModList").invoke(loader);
            if (!(indexed instanceof Map<?, ?> mods)) {
                return Optional.empty();
            }
            Object container = mods.get("zstdnet");
            if (container == null) {
                return Optional.empty();
            }

            try {
                Object version = container.getClass().getMethod("getVersion").invoke(container);
                if (version != null && !version.toString().isBlank()) {
                    return Optional.of(version.toString());
                }
            } catch (ReflectiveOperationException ignored) {
                // Forge 1.12 exposes the version through ModMetadata instead.
            }

            Object metadata = container.getClass().getMethod("getMetadata").invoke(container);
            Field version = metadata.getClass().getField("version");
            Object value = version.get(metadata);
            return value == null || value.toString().isBlank()
                    ? Optional.empty() : Optional.of(value.toString());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            MiguelNetwork.LOGGER.debug("Could not inspect the loaded Mod list for ZstdNet", exception);
            return Optional.empty();
        }
    }
}
