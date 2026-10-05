package io.github.therealkamisama.miguelnetwork.mixin;

import io.github.therealkamisama.miguelnetwork.client.ClientConnectionAttempt;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.multiplayer.GuiConnecting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiConnecting.class)
public abstract class GuiConnectingMixin {
    @Unique private ClientConnectionAttempt miguelnetwork$attempt;

    @Redirect(method = "func_146367_a(Ljava/lang/String;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/GuiConnecting$1;start()V", remap = false), require = 1)
    private void miguelnetwork$captureAttempt(@Coerce Thread thread) {
        miguelnetwork$attempt = ClientConnectionAttempt.register(thread);
        thread.start();
    }

    @Inject(method = "actionPerformed(Lnet/minecraft/client/gui/GuiButton;)V", at = @At("RETURN"), require = 1)
    private void miguelnetwork$cancelAttempt(GuiButton button, CallbackInfo callback) {
        if (button.id == 0 && miguelnetwork$attempt != null) miguelnetwork$attempt.cancel();
    }
}
