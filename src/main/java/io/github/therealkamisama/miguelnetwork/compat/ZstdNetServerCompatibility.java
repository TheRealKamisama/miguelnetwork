package io.github.therealkamisama.miguelnetwork.compat;

import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.OptionalInt;

public final class ZstdNetServerCompatibility {
    public static final String SUPPORTED_VERSION = "1.4.7";

    private ZstdNetServerCompatibility() {
    }

    public static OptionalInt discoverListenPort() {
        var container = ModList.get().getModContainerById("zstdnet");
        if (container.isEmpty()) {
            return OptionalInt.empty();
        }
        String version = container.get().getModInfo().getVersion().toString();
        if (!isSupportedVersion(version)) {
            MiguelNetwork.LOGGER.warn(
                    "ZstdNet {} is installed, but MiguelNetwork only enables its reflective adapter for {}",
                    version, SUPPORTED_VERSION
            );
            return OptionalInt.empty();
        }
        try {
            Class<?> config = Class.forName("cn.tohsaka.factory.zstdnet.server.ServerProxyConfigFile");
            Method readListenPort = config.getMethod("readListenPort");
            int port = (Integer) readListenPort.invoke(null);
            if (port < 1 || port > 65535) {
                throw new IllegalStateException("ZstdNet returned invalid listen port " + port);
            }
            MiguelNetwork.LOGGER.info("MiguelNetwork detected supported ZstdNet {} listener port {}", version, port);
            return OptionalInt.of(port);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            MiguelNetwork.LOGGER.warn("Cannot inspect the installed ZstdNet server; compatibility route is disabled", exception);
            return OptionalInt.empty();
        }
    }

    public static boolean isSupportedVersion(String version) {
        return SUPPORTED_VERSION.equals(version);
    }
}
