package io.github.therealkamisama.miguelnetwork.bootstrap;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.util.List;
import java.util.Map;
import java.util.Set;

@IFMLLoadingPlugin.Name("MiguelNetwork")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions("io.github.therealkamisama.miguelnetwork.bootstrap")
public final class MiguelNetworkCore implements IFMLLoadingPlugin, IEarlyMixinLoader {
    @Override public String getMixinConfig() { return "mixins.miguelnetwork.early.json"; }
    @Override public List<String> getMixins(Set<String> loadedCoreMods) {
        return FMLLaunchHandler.side().isClient() ? List.of("NetworkManagerMixin", "GuiConnectingMixin", "OldServerPingerMixin") : List.of();
    }
    @Override public String[] getASMTransformerClass() { return new String[0]; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) { }
    @Override public String getAccessTransformerClass() { return null; }
}
