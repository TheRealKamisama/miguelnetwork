package io.github.therealkamisama.miguelnetwork.compat;

import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import io.github.therealkamisama.miguelnetwork.client.ClientTunnelManager;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Optional;

public final class ZstdNetClientCompatibility {
    private static final String HOOKS = "cn.tohsaka.factory.zstdnet.coremod.ConnectScreenHooks";
    private static final String PROXY = "cn.tohsaka.factory.zstdnet.proxy.LocalZstdNet";
    private static volatile Boolean supported;

    private ZstdNetClientCompatibility() {
    }

    public static boolean isSupported() {
        Boolean cached = supported;
        if (cached != null) {
            return cached;
        }

        boolean result = ModList.get().getModContainerById("zstdnet")
                .map(container -> ZstdNetServerCompatibility.isSupportedVersion(
                        container.getModInfo().getVersion().toString()))
                .orElse(false);
        if (ModList.get().isLoaded("zstdnet") && !result) {
            String version = ModList.get().getModContainerById("zstdnet")
                    .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
            MiguelNetwork.LOGGER.warn("MiguelNetwork ZstdNet client adapter disabled for unsupported version {}", version);
        } else if (result) {
            String version = ModList.get().getModContainerById("zstdnet")
                    .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
            MiguelNetwork.LOGGER.info("MiguelNetwork enabled its ZstdNet {} compatibility adapter", version);
        }
        supported = result;
        return result;
    }

    public static Optional<ServerAddress> tryIntercept(
            ServerAddress original,
            ServerData serverData,
            boolean zstdNetBypassing
    ) {
        if (original == null || !isSupported()) {
            return Optional.empty();
        }
        ServerAddress logicalAddress = logicalAddress(original, serverData, zstdNetBypassing).orElse(null);
        if (logicalAddress == null) {
            return Optional.empty();
        }
        InetSocketAddress logical = InetSocketAddress.createUnresolved(
                logicalAddress.getHost(), logicalAddress.getPort()
        );
        Optional<ClientTunnelManager.PreparedTunnel> prepared = ClientTunnelManager.prepareForZstdNet(logical);
        if (prepared.isEmpty()) {
            return Optional.empty();
        }

        Object proxy = null;
        try {
            proxy = startProxy(prepared.get().address(), logicalAddress);
            int localPort = (Integer) proxy.getClass().getMethod("localPort").invoke(proxy);
            ServerAddress localAddress = ServerAddress.parseString("127.0.0.1:" + localPort);
            publishProxyHandle(proxy);
            ClientTunnelManager.registerInternalLoopbackPort(localPort);
            if (serverData != null) {
                serverData.ip = formatHostPort(logicalAddress.getHost(), logicalAddress.getPort());
            }
            MiguelNetwork.LOGGER.info(
                    "MiguelNetwork composed ZstdNet -> {} -> route {} for {}",
                    prepared.get().address(), prepared.get().route().id(),
                    serverData == null ? logicalAddress : serverData.ip
            );
            return Optional.of(localAddress);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            closeQuietly(proxy);
            MiguelNetwork.LOGGER.warn(
                    "MiguelNetwork could not compose the supported ZstdNet adapter; using ZstdNet's original path",
                    unwrap(exception)
            );
            return Optional.empty();
        }
    }

    private static Optional<ServerAddress> logicalAddress(
            ServerAddress original,
            ServerData serverData,
            boolean zstdNetBypassing
    ) {
        if (!isLoopback(original.getHost())) {
            return Optional.of(original);
        }
        if (!zstdNetBypassing || serverData == null || serverData.ip == null
                || !ServerAddress.isValidAddress(serverData.ip)) {
            return Optional.empty();
        }
        ServerAddress restored = ServerAddress.parseString(serverData.ip);
        return isLoopback(restored.getHost()) ? Optional.empty() : Optional.of(restored);
    }

    private static void closeQuietly(Object proxy) {
        if (proxy instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object startProxy(InetSocketAddress tunnel, ServerAddress logical)
            throws ReflectiveOperationException {
        Class<?> proxyClass = Class.forName(PROXY);
        Class<? extends Enum> modeClass = (Class<? extends Enum>) Class.forName(PROXY + "$Mode").asSubclass(Enum.class);
        Object zstd = Enum.valueOf(modeClass, "ZSTD");
        Class<?> clientConfig = Class.forName("cn.tohsaka.factory.zstdnet.ClientConfig");
        int level = (Integer) clientConfig.getMethod("getLevel").invoke(null);
        Method start = proxyClass.getMethod(
                "start",
                String.class, int.class,
                String.class, int.class,
                String.class, int.class,
                int.class, modeClass
        );
        return start.invoke(
                null,
                tunnel.getHostString(), tunnel.getPort(),
                tunnel.getHostString(), tunnel.getPort(),
                logical.getHost(), logical.getPort(),
                level, zstd
        );
    }

    private static void publishProxyHandle(Object proxy) throws ReflectiveOperationException {
        Class<?> hooks = Class.forName(HOOKS);
        Field lockField = hooks.getDeclaredField("LOCK");
        Field proxyField = hooks.getDeclaredField("currentProxy");
        lockField.setAccessible(true);
        proxyField.setAccessible(true);
        Object lock = lockField.get(null);
        synchronized (lock) {
            Object old = proxyField.get(null);
            if (old instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                }
            }
            proxyField.set(null, proxy);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        return throwable instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : throwable;
    }

    private static boolean isLoopback(String host) {
        return host.equals("127.0.0.1") || host.equals("localhost") || host.equals("::1");
    }

    private static String formatHostPort(String host, int port) {
        String normalized = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1) : host;
        return normalized.indexOf(':') >= 0 ? "[" + normalized + "]:" + port : normalized + ":" + port;
    }
}
