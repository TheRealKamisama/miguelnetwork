package io.github.therealkamisama.miguelnetwork.mixin;

import io.github.therealkamisama.miguelnetwork.client.ClientTunnelManager;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.net.InetSocketAddress;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @ModifyVariable(method = "connect", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static InetSocketAddress miguelnetwork$redirectConnection(InetSocketAddress original) {
        return ClientTunnelManager.redirect(original);
    }
}

