package io.github.therealkamisama.miguelnetwork.mixin;

import io.github.therealkamisama.miguelnetwork.compat.ZstdNetClientCompatibility;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "cn.tohsaka.factory.zstdnet.coremod.ConnectScreenHooks", remap = false)
public abstract class ZstdNetConnectScreenHooksMixin {
    @Inject(method = "interceptConnect", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void miguelnetwork$composeZstdNet(
            ServerAddress original,
            ServerData serverData,
            CallbackInfoReturnable<ServerAddress> callback
    ) {
        ZstdNetClientCompatibility.tryIntercept(original, serverData).ifPresent(callback::setReturnValue);
    }
}
