package io.github.therealkamisama.miguelnetwork;

import io.github.therealkamisama.miguelnetwork.config.ServerConfig;
import io.github.therealkamisama.miguelnetwork.config.WstunnelDownloadConfig;
import io.github.therealkamisama.miguelnetwork.server.ServerTunnelController;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.fabric.api.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MiguelNetwork implements ModInitializer {
    public static final String MOD_ID = "miguelnetwork";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        WstunnelDownloadConfig.SPEC.load(FabricLoader.getInstance().getConfigDir().resolve("miguelnetwork-common.toml"));
        ServerConfig.SPEC.load(FabricLoader.getInstance().getConfigDir().resolve("miguelnetwork-server.toml"));
        // ZstdNet opens its listener in the default SERVER_STARTED phase.
        ResourceLocation gatewayPhase = new ResourceLocation(MOD_ID, "gateway");
        ServerLifecycleEvents.SERVER_STARTED.addPhaseOrdering(Event.DEFAULT_PHASE, gatewayPhase);
        ServerLifecycleEvents.SERVER_STARTED.register(gatewayPhase, server -> {
            if (server.isDedicatedServer()) ServerTunnelController.start(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ServerTunnelController.stop());
        Runtime.getRuntime().addShutdownHook(new Thread(ServerTunnelController::stop, "MiguelNetwork-server-shutdown"));
        LOGGER.info("MiguelNetwork loaded");
    }
}
