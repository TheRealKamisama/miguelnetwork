package io.github.therealkamisama.miguelnetwork;

import io.github.therealkamisama.miguelnetwork.client.ClientTunnelManager;
import io.github.therealkamisama.miguelnetwork.config.ClientConfig;
import io.github.therealkamisama.miguelnetwork.config.ServerConfig;
import io.github.therealkamisama.miguelnetwork.server.ServerTunnelController;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

@Mod(modid = MiguelNetwork.MOD_ID, name = "MiguelNetwork", version = BuildInfo.VERSION,
        acceptedMinecraftVersions = "[1.12.2]", acceptableRemoteVersions = "*",
        dependencies = "required-after:cleanroom@[" + BuildInfo.CLEANROOM_VERSION + "]")
public final class MiguelNetwork {
    public static final String MOD_ID = "miguelnetwork";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static Path gameDirectory;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Path config = event.getModConfigurationDirectory().toPath().toAbsolutePath();
        gameDirectory = config.getParent();
        ServerConfig.SPEC.load(config.resolve("miguelnetwork-server.toml"));
        if (event.getSide().isClient()) {
            ClientConfig.SPEC.load(config.resolve("miguelnetwork-client.toml"));
            ClientTunnelManager.initialize(gameDirectory);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(MiguelNetwork::stopSidecars, "MiguelNetwork-shutdown"));
        LOGGER.info("MiguelNetwork loaded");
    }

    public static Path gameDirectory() {
        if (gameDirectory == null) throw new IllegalStateException("MiguelNetwork has not initialized");
        return gameDirectory;
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        var server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server.isDedicatedServer()) ServerTunnelController.start(gameDirectory(), server.getServerPort());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        ServerTunnelController.stop();
    }

    public static void stopSidecars() {
        // ClientTunnelManager uses only JDK/config classes; no client-only Minecraft classes are loaded here.
        ClientTunnelManager.stop();
        ServerTunnelController.stop();
    }
}
