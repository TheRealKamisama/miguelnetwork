package io.github.therealkamisama.miguelnetwork.compat;

import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import io.github.therealkamisama.miguelnetwork.client.ClientTunnelManager;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.fml.ModList;

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
            MiguelNetwork.LOGGER.info("MiguelNetwork enabled its ZstdNet {} compatibility adapter",
                    ZstdNetServerCompatibility.SUPPORTED_VERSION);
        }
        supported = result;
        return result;
    }

    public static ServerAddress intercept(ServerAddress original, ServerData serverData) {
        if (original == null || !isSupported() || isLoopback(original.getHost())) {
            return invokeOriginal(original, serverData);
        }
        InetSocketAddress logical = InetSocketAddress.createUnresolved(original.getHost(), original.getPort());
        Optional<ClientTunnelManager.PreparedTunnel> prepared = ClientTunnelManager.prepareForZstdNet(logical);
        if (prepared.isEmpty()) {
            return invokeOriginal(original, serverData);
        }

        Object proxy = null;
        try {
            proxy = startProxy(prepared.get().address(), original);
            int localPort = (Integer) proxy.getClass().getMethod("localPort").invoke(proxy);
            ServerAddress localAddress = ServerAddress.parseString("127.0.0.1:" + localPort);
            publishProxyHandle(proxy);
            ClientTunnelManager.registerInternalLoopbackPort(localPort);
            if (serverData != null) {
                serverData.ip = formatHostPort(original.getHost(), original.getPort());
            }
            MiguelNetwork.LOGGER.info(
                    "MiguelNetwork composed ZstdNet -> {} -> route {} for {}",
                    prepared.get().address(), prepared.get().route().id(), serverData == null ? original : serverData.ip
            );
            return localAddress;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            closeQuietly(proxy);
            MiguelNetwork.LOGGER.warn(
                    "MiguelNetwork could not compose the supported ZstdNet adapter; using ZstdNet's original path",
                    unwrap(exception)
            );
            return invokeOriginal(original, serverData);
        }
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

    private static ServerAddress invokeOriginal(ServerAddress original, ServerData serverData) {
        if (!ModList.get().isLoaded("zstdnet")) {
            return original;
        }
        try {
            Class<?> hooks = Class.forName(HOOKS);
            return (ServerAddress) hooks.getMethod("interceptConnect", ServerAddress.class, ServerData.class)
                    .invoke(null, original, serverData);
        } catch (ReflectiveOperationException exception) {
            MiguelNetwork.LOGGER.warn("Cannot invoke ZstdNet's original connection hook", unwrap(exception));
            return original;
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
