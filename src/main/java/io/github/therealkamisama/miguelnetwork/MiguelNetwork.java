package io.github.therealkamisama.miguelnetwork;

import com.mojang.logging.LogUtils;
import io.github.therealkamisama.miguelnetwork.client.ClientTunnelManager;
import io.github.therealkamisama.miguelnetwork.config.ClientConfig;
import io.github.therealkamisama.miguelnetwork.config.ServerConfig;
import io.github.therealkamisama.miguelnetwork.server.ServerTunnelController;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@Mod(MiguelNetwork.MOD_ID)
public final class MiguelNetwork {
    public static final String MOD_ID = "miguelnetwork";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MiguelNetwork(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, "miguelnetwork-client.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, "miguelnetwork-server.toml");
        NeoForge.EVENT_BUS.register(this);
        Runtime.getRuntime().addShutdownHook(new Thread(MiguelNetwork::stopSidecars, "MiguelNetwork-shutdown"));
        LOGGER.info("MiguelNetwork loaded");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerTunnelController.start(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ServerTunnelController.stop();
    }

    @SubscribeEvent
    public void onGameShuttingDown(GameShuttingDownEvent event) {
        stopSidecars();
    }

    private static void stopSidecars() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientTunnelManager.stop();
        }
        ServerTunnelController.stop();
    }
}
