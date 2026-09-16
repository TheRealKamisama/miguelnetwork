package io.github.therealkamisama.miguelnetwork;

import com.mojang.logging.LogUtils;
import io.github.therealkamisama.miguelnetwork.client.ClientTunnelManager;
import io.github.therealkamisama.miguelnetwork.config.ClientConfig;
import io.github.therealkamisama.miguelnetwork.config.ServerConfig;
import io.github.therealkamisama.miguelnetwork.server.ServerTunnelController;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(MiguelNetwork.MOD_ID)
public final class MiguelNetwork {
    public static final String MOD_ID = "miguelnetwork";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MiguelNetwork() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, "miguelnetwork-client.toml");
        // COMMON configs are materialized in config/. Forge SERVER configs live under a world's serverconfig
        // directory, which made the documented dedicated-server file appear to be missing.
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ServerConfig.SPEC, "miguelnetwork-server.toml");
        MinecraftForge.EVENT_BUS.register(this);
        Runtime.getRuntime().addShutdownHook(new Thread(MiguelNetwork::stopSidecars, "MiguelNetwork-shutdown"));
        LOGGER.info("MiguelNetwork loaded");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (event.getServer().isDedicatedServer()) {
            ServerTunnelController.start(event.getServer());
        }
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
