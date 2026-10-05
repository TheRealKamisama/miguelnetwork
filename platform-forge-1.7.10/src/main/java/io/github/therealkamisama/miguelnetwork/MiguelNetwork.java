package io.github.therealkamisama.miguelnetwork;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import io.github.therealkamisama.miguelnetwork.config.ClientConfig;
import io.github.therealkamisama.miguelnetwork.config.ServerConfig;
import io.github.therealkamisama.miguelnetwork.config.WstunnelDownloadConfig;
import io.github.therealkamisama.miguelnetwork.client.ClientTunnelManager;
import io.github.therealkamisama.miguelnetwork.server.ServerTunnelController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
@Mod(modid = MiguelNetwork.MOD_ID, name = "MiguelNetwork", version = Tags.VERSION,
     acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "*")
public final class MiguelNetwork {
    public static final String MOD_ID = "miguelnetwork";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        PlatformPaths.initialize(event.getModConfigurationDirectory().toPath());
        try {
            var config = event.getModConfigurationDirectory().toPath();
            ServerConfig.SPEC.load(config.resolve("miguelnetwork-server.properties"));
            WstunnelDownloadConfig.SPEC.load(config.resolve("miguelnetwork-common.properties"));
            if (event.getSide().isClient()) {
                ClientConfig.SPEC.load(config.resolve("miguelnetwork-client.properties"));
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot load MiguelNetwork configuration", exception);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(MiguelNetwork::stopSidecars, "MiguelNetwork-shutdown"));
        LOGGER.info("MiguelNetwork loaded on Java {}", Runtime.version());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        var server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server.isDedicatedServer()) ServerTunnelController.start(server);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        ServerTunnelController.stop();
    }

    private static void stopSidecars() {
        ClientTunnelManager.stop();
        ServerTunnelController.stop();
    }
}
