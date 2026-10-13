package io.github.therealkamisama.miguelnetwork.probe;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.OldServerPinger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Opt-in, separately packaged local runtime acceptance driver. */
@Mod(modid = "miguelnetwork_runtime_probe", name = "MiguelNetwork Runtime Probe", version = "1",
        acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "*")
public final class RuntimeProbe {
    private static final Logger LOG = LogManager.getLogger("MiguelNetworkRuntimeProbe");
    private final OldServerPinger pinger = new OldServerPinger();
    private final String endpoint = System.getProperty("miguelnetwork.probe.endpoint", "127.0.0.1:35576");
    private final boolean expectCertificateFailure = Boolean.getBoolean("miguelnetwork.probe.expectCertificateFailure");
    private final String restartMarker = System.getProperty("miguelnetwork.probe.restartMarker", "");
    private ServerData status;
    private volatile Throwable pingFailure;
    private long started;
    private int ticks;
    private int phase;
    private int playTicks;
    private int joins;
    private final boolean diagnostics = Boolean.getBoolean("miguelnetwork.probe.diagnostics");
    private final boolean requireNative = Boolean.getBoolean("miguelnetwork.probe.requireNative");
    private boolean reportRequested, reportChecked;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        started = System.nanoTime();
        FMLCommonHandler.instance().bus().register(this);
        LOG.info("PROBE START Java={} endpoint={}", Runtime.version(), endpoint);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase == 9) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (System.nanoTime() - started > java.time.Duration.ofMinutes(3).toNanos()) {
                throw new IllegalStateException("Timed out in phase " + phase + ", screen=" + mc.currentScreen);
            }
            if (pingFailure != null) throw new IllegalStateException("Status failed", pingFailure);
            ticks++;
            if (diagnostics && ticks % 100 == 0) inspectConnection(mc);
            if (phase == 0 && ticks > 40 && mc.currentScreen instanceof GuiMainMenu) {
                FMLClientHandler.instance().setupServerList();
                status = new ServerData("Runtime acceptance", endpoint);
                status.pingToServer = -2;
                phase = 1;
                Thread.ofVirtual().start(() -> {
                    try { pinger.func_147224_a(status); }
                    catch (Throwable error) { pingFailure = error; }
                });
            } else if (phase == 1) {
                pinger.func_147223_a();
                if (status.pingToServer >= 0) {
                    if (expectCertificateFailure) throw new IllegalStateException("Untrusted WSS certificate was accepted");
                    LOG.info("PROBE PASS status ping={}ms version={} population={}",
                            status.pingToServer, status.gameVersion, status.populationInfo);
                    pinger.func_147226_b();
                    var cancelled = new CancelConnection(mc, endpoint);
                    mc.displayGuiScreen(cancelled);
                    cancelled.cancel();
                    phase = 4;
                    ticks = 0;
                } else if (expectCertificateFailure && status.serverMOTD != null
                        && status.serverMOTD.contains("Can't connect")) {
                    pinger.func_147226_b();
                    phase = 5;
                    ticks = 0;
                }
            } else if (phase == 5 && ticks > 40) {
                if (mc.theWorld != null || ProcessHandle.current().descendants().anyMatch(p ->
                        p.info().command().orElse("").toLowerCase(java.util.Locale.ROOT).contains("wstunnel"))) {
                    throw new IllegalStateException("Rejected WSS route retained a world or sidecar");
                }
                LOG.info("PROBE PASS expected certificate rejection; no world or lingering sidecar");
                phase = 9;
                mc.shutdown();
            } else if (phase == 4 && ticks > 240) {
                if (ProcessHandle.current().descendants().anyMatch(p ->
                        p.info().command().orElse("").toLowerCase(java.util.Locale.ROOT).contains("wstunnel"))) {
                    throw new IllegalStateException("Cancelled attempt left a wstunnel process running");
                }
                LOG.info("PROBE PASS cancel with no lingering sidecar");
                join(mc);
            } else if (phase == 2 && mc.theWorld != null && mc.thePlayer != null) {
                playTicks++;
                if (requireNative && joins == 0 && playTicks == 60) {
                    Object stats = Class.forName("cn.tohsaka.factory.zstdnet.client.ClientConnection")
                            .getMethod("snapshot").invoke(null);
                    if (stats == null || !"ZSTD".equals(stats.getClass().getMethod("mode").invoke(stats).toString())
                            || ((Number) stats.getClass().getMethod("rawUpBytes").invoke(stats)).longValue() <= 0
                            || ((Number) stats.getClass().getMethod("rawDownBytes").invoke(stats)).longValue() <= 0) {
                        throw new IllegalStateException("Native HUD statistics unavailable");
                    }
                    LOG.info("PROBE PASS native HUD statistics");
                    net.minecraftforge.client.ClientCommandHandler.instance.executeCommand(mc.thePlayer, "/zstdreport session");
                    reportRequested = true;
                }
                if (reportRequested && !reportChecked) {
                    var folder = mc.mcDataDir.toPath().resolve("config/zstdnet/reports");
                    if (java.nio.file.Files.isDirectory(folder)) {
                        try (var files = java.nio.file.Files.walk(folder)) {
                            reportChecked = files.anyMatch(file -> file.toString().endsWith(".html"));
                        }
                    }
                    if (reportChecked) LOG.info("PROBE PASS native report received and rendered");
                }
                if (requireNative && joins == 0 && playTicks > 500 && !reportChecked) {
                    throw new IllegalStateException("Native report did not arrive");
                }
                if (playTicks == 40) mc.thePlayer.sendChatMessage("/help");
                if (playTicks >= 100 && mc.thePlayer.ticksExisted >= 80
                        && (!requireNative || joins != 0 || reportChecked)) {
                    LOG.info("PROBE PASS gameplay join={} entity={} ticks={} position={},{},{}",
                            ++joins, mc.thePlayer.getEntityId(), mc.thePlayer.ticksExisted,
                            mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
                    if (!restartMarker.isEmpty() && joins == 1) {
                        LOG.info("PROBE REQUEST server restart while connected");
                        phase = 6;
                        return;
                    }
                    mc.theWorld.sendQuittingDisconnectingPacket();
                    mc.loadWorld(null);
                    mc.displayGuiScreen(new GuiMainMenu());
                    phase = 3;
                    ticks = 0;
                }
            } else if (phase == 6 && mc.theWorld == null && mc.currentScreen instanceof GuiDisconnected) {
                LOG.info("PROBE PASS server interruption disconnected the gameplay channel");
                phase = 7;
            } else if (phase == 7 && java.nio.file.Files.exists(java.nio.file.Path.of(restartMarker))) {
                LOG.info("PROBE reconnecting after verified server restart");
                join(mc);
            } else if (phase == 3 && ticks > 40) {
                if (joins < 2) join(mc);
                else {
                    LOG.info("PROBE PASS disconnect and reconnect; runtime acceptance driver finished");
                    phase = 9;
                    mc.shutdown();
                }
            }
        } catch (Throwable failure) {
            LOG.error("PROBE FAIL phase=" + phase, failure);
            phase = 9;
            pinger.func_147226_b();
            mc.shutdown();
        }
    }

    private void join(Minecraft mc) {
        playTicks = 0;
        phase = 2;
        FMLClientHandler.instance().connectToServer(new GuiMainMenu(),
                joins == 0 ? status : new ServerData("Runtime direct reconnect", endpoint));
    }

    private void inspectConnection(Minecraft mc) throws Exception {
        LOG.info("PROBE STATE phase={} screen={} ping={} motd={}", phase,
                mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getName(),
                status == null ? null : status.pingToServer, status == null ? null : status.serverMOTD);
        if (mc.currentScreen instanceof GuiDisconnected) {
            for (var field : GuiDisconnected.class.getDeclaredFields()) {
                if (!net.minecraft.util.IChatComponent.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                var reason = (net.minecraft.util.IChatComponent) field.get(mc.currentScreen);
                LOG.info("PROBE disconnect reason: {}", reason.getUnformattedText());
            }
        }
        if (mc.currentScreen instanceof GuiConnecting) {
            for (var field : GuiConnecting.class.getDeclaredFields()) {
                if (field.getType() != net.minecraft.network.NetworkManager.class) continue;
                field.setAccessible(true);
                inspectManager((net.minecraft.network.NetworkManager) field.get(mc.currentScreen));
            }
        }
        for (var field : OldServerPinger.class.getDeclaredFields()) {
            if (!java.util.List.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            var list = (java.util.List<?>) field.get(pinger);
            synchronized (list) {
                LOG.info("PROBE pending status managers={}", list.size());
                for (Object manager : list) inspectManager((net.minecraft.network.NetworkManager) manager);
            }
        }
    }

    private void inspectManager(net.minecraft.network.NetworkManager manager) throws Exception {
        if (manager == null) { LOG.info("PROBE manager not yet assigned"); return; }
        LOG.info("PROBE channel open={} reason={} handler={}", manager.isChannelOpen(), manager.getExitMessage(),
                manager.getNetHandler() == null ? "none" : manager.getNetHandler().getClass().getName());
        for (var field : net.minecraft.network.NetworkManager.class.getDeclaredFields()) {
            if (field.getType() != io.netty.channel.Channel.class) continue;
            field.setAccessible(true);
            var channel = (io.netty.channel.Channel) field.get(manager);
            if (channel == null) return;
            LOG.info("PROBE netty active={} registered={} autoread={} writable={}", channel.isActive(),
                    channel.isRegistered(), channel.config().isAutoRead(), channel.isWritable());
        }
    }

    private static final class CancelConnection extends GuiConnecting {
        CancelConnection(Minecraft mc, String endpoint) {
            super(new GuiMainMenu(), mc, new ServerData("Cancelled acceptance attempt", endpoint));
        }
        void cancel() { super.actionPerformed(new GuiButton(0, 0, 0, "Cancel")); }
    }
}
