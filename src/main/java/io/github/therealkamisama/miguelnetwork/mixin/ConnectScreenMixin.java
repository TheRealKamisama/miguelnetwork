package io.github.therealkamisama.miguelnetwork.mixin;

import io.github.therealkamisama.miguelnetwork.compat.ZstdNetClientCompatibility;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
    @Redirect(
            method = "startConnecting",
            at = @At(
                    value = "INVOKE",
                    target = "Lcn/tohsaka/factory/zstdnet/coremod/ConnectScreenHooks;interceptConnect(Lnet/minecraft/client/multiplayer/resolver/ServerAddress;Lnet/minecraft/client/multiplayer/ServerData;)Lnet/minecraft/client/multiplayer/resolver/ServerAddress;",
                    remap = false
            ),
            require = 0
    )
    private static ServerAddress miguelnetwork$composeZstdNet(
            ServerAddress original,
            ServerData serverData
    ) {
        return ZstdNetClientCompatibility.intercept(original, serverData);
    }
}
