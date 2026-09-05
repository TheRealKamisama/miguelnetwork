package io.github.therealkamisama.miguelnetwork;

import com.mojang.logging.LogUtils;
import io.github.therealkamisama.miguelnetwork.server.ServerTunnelController;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@Mod(MiguelNetwork.MOD_ID)
public final class MiguelNetwork {
    public static final String MOD_ID = "miguelnetwork";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MiguelNetwork(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("MiguelNetwork Phase 0 loaded");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerTunnelController.start(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ServerTunnelController.stop();
    }
}

