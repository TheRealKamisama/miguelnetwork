package io.github.therealkamisama.miguelnetwork.client;

import io.github.therealkamisama.miguelnetwork.config.ClientConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class MiguelNetworkClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientConfig.SPEC.load(FabricLoader.getInstance().getConfigDir().resolve("miguelnetwork-client.toml"));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClientTunnelManager.stop());
        Runtime.getRuntime().addShutdownHook(new Thread(ClientTunnelManager::stop, "MiguelNetwork-client-shutdown"));
    }
}
