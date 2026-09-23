package io.github.therealkamisama.miguelnetwork.mixin;

import io.github.therealkamisama.miguelnetwork.compat.ZstdNetClientCompatibility;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional bridge to the Cleanroom ZstdNet client lifecycle. */
@Pseudo
@Mixin(targets = "cn.tohsaka.factory.zstdnet.client.ClientProxy", remap = false)
public abstract class ZstdNetClientProxyMixin {
    @Inject(
            method = "startConnection(Ljava/lang/String;ILjava/lang/String;Lnet/minecraft/client/gui/GuiScreen;)J",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false
    )
    private static void miguelnetwork$compose(
            String host,
            int port,
            String destination,
            GuiScreen parent,
            CallbackInfoReturnable<Long> callback
    ) {
        ZstdNetClientCompatibility.tryStartConnection(host, port, destination, parent)
                .ifPresent(attempt -> callback.setReturnValue(attempt));
    }
}
