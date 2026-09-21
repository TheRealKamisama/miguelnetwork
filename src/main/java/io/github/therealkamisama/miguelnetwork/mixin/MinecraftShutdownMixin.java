package io.github.therealkamisama.miguelnetwork.mixin;

import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftShutdownMixin {
    @Inject(method = "shutdownMinecraftApplet()V", at = @At("HEAD"))
    private void miguelnetwork$stopSidecars(CallbackInfo callback) {
        MiguelNetwork.stopSidecars();
    }
}
