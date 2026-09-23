package io.github.therealkamisama.miguelnetwork.compat;

import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import io.github.therealkamisama.miguelnetwork.client.ClientTunnelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Reflective client adapter for the Cleanroom ZstdNet alpha.
 *
 * <p>The Cleanroom port has no public address-replacement SPI. Its stable
 * boundary is the private {@code ClientProxy.startConnection} lifecycle and
 * the public {@code LocalZstdNet.start(...)} / {@code InternalConnectGuard}
 * helpers. The optional mixin invokes this class only when ZstdNet is loaded;
 * all references to its classes remain reflective.</p>
 */
public final class ZstdNetClientCompatibility {
    private static final String CLIENT_PROXY = "cn.tohsaka.factory.zstdnet.client.ClientProxy";
    private static final String CLIENT_SETTINGS = "cn.tohsaka.factory.zstdnet.client.ClientSettings";
    private static final String INTERNAL_GUARD = "cn.tohsaka.factory.zstdnet.client.InternalConnectGuard";
    private static final String LOCAL_PROXY = "cn.tohsaka.factory.zstdnet.proxy.LocalZstdNet";
    private static volatile Boolean supported;

    private ZstdNetClientCompatibility() {
    }

    public static boolean isSupported() {
        Boolean cached = supported;
        if (cached != null) {
            return cached;
        }
        Optional<String> version = ZstdNetServerCompatibility.installedVersion();
        boolean result = version.isPresent() && ZstdNetServerCompatibility.isSupportedVersion(version.get());
        if (version.isPresent() && !result) {
            MiguelNetwork.LOGGER.warn(
                    "ZstdNet client adapter disabled for unsupported version {} (supported {})",
                    version.get(), ZstdNetServerCompatibility.SUPPORTED_VERSION
            );
        } else if (result) {
            MiguelNetwork.LOGGER.info(
                    "MiguelNetwork enabled its ZstdNet {} compatibility adapter", version.get());
        }
        supported = result;
        return result;
    }

    public static boolean isSupportedVersion(String version) {
        return ZstdNetServerCompatibility.isSupportedVersion(version);
    }

    /**
     * Starts a composed ZstdNet connection and returns its attempt number.
     * An empty result leaves ZstdNet's original connection path untouched.
     */
    public static OptionalLong tryStartConnection(
            String host,
            int port,
            String destination,
            GuiScreen parent
    ) {
        if (!isSupported()) {
            return OptionalLong.empty();
        }

        InetSocketAddress logical = InetSocketAddress.createUnresolved(host, port);
        ClientTunnelManager.PreparedTunnel prepared;
        try {
            prepared = ClientTunnelManager.prepareForZstdNet(logical).orElse(null);
        } catch (RuntimeException exception) {
            MiguelNetwork.LOGGER.warn(
                    "MiguelNetwork could not prepare the ZstdNet compatibility route; using ZstdNet's original path",
                    unwrap(exception)
            );
            return OptionalLong.empty();
        }
        if (prepared == null) {
            return OptionalLong.empty();
        }

        Object proxy = null;
        try {
            Class<?> clientProxy = Class.forName(CLIENT_PROXY);
            invokeStatic(clientProxy, "close");

            Field returnScreen = accessibleField(clientProxy, "returnScreen");
            Field generation = accessibleField(clientProxy, "generation");
            returnScreen.set(null, parent);
            updateClientSettings(destination);
            long attempt = ((AtomicLong) generation.get(null)).incrementAndGet();

            proxy = startProxy(prepared.address(), logical);
            int localPort = ((Number) proxy.getClass().getMethod("localPort").invoke(proxy)).intValue();
            publishProxyHandle(clientProxy, proxy);
            ClientTunnelManager.registerInternalLoopbackPort(localPort);

            Minecraft minecraft = Minecraft.getMinecraft();
            Object internal = constructInternalGui(parent, minecraft, localPort);
            minecraft.displayGuiScreen((GuiConnecting) internal);
            MiguelNetwork.LOGGER.info(
                    "MiguelNetwork composed ZstdNet through route {} for {}:{} via 127.0.0.1:{}",
                    prepared.route().id(), host, port, localPort
            );
            return OptionalLong.of(attempt);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            closeQuietly(proxy);
            MiguelNetwork.LOGGER.warn(
                    "MiguelNetwork could not compose the supported ZstdNet adapter; using ZstdNet's original path",
                    unwrap(exception)
            );
            return OptionalLong.empty();
        }
    }

    private static Object startProxy(InetSocketAddress tunnel, InetSocketAddress logical)
            throws ReflectiveOperationException {
        Class<?> proxyClass = Class.forName(LOCAL_PROXY);
        Class<? extends Enum> modeClass = Class.forName(LOCAL_PROXY + "$Mode").asSubclass(Enum.class);
        Object zstdMode = Enum.valueOf(modeClass, "ZSTD");
        Class<?> settings = Class.forName(CLIENT_SETTINGS);
        Field levelField = accessibleField(settings, "level");
        int level = ((Number) levelField.get(null)).intValue();
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
                logical.getHostString(), logical.getPort(),
                level, zstdMode
        );
    }

    private static Object constructInternalGui(GuiScreen parent, Minecraft minecraft, int localPort)
            throws ReflectiveOperationException {
        Class<?> guard = Class.forName(INTERNAL_GUARD);
        Method construct = guard.getMethod("construct", Supplier.class);
        Supplier<Object> constructor = () -> new GuiConnecting(parent, minecraft, "127.0.0.1", localPort);
        return construct.invoke(null, constructor);
    }

    private static void publishProxyHandle(Class<?> clientProxy, Object proxy)
            throws ReflectiveOperationException {
        accessibleField(clientProxy, "connection").set(null, proxy);
    }

    private static void updateClientSettings(String destination) {
        try {
            Class<?> settings = Class.forName(CLIENT_SETTINGS);
            accessibleField(settings, "target").set(null, destination);
            settings.getDeclaredMethod("save").setAccessible(true);
            settings.getDeclaredMethod("save").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            MiguelNetwork.LOGGER.debug("Could not persist the ZstdNet composed destination", exception);
        }
    }

    private static Field accessibleField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void invokeStatic(Class<?> type, String name) throws ReflectiveOperationException {
        type.getMethod(name).invoke(null);
    }

    private static void closeQuietly(Object proxy) {
        if (proxy instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        return throwable instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : throwable;
    }
}
