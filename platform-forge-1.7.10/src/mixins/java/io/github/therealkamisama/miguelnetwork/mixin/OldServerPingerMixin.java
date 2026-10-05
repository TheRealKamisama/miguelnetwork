package io.github.therealkamisama.miguelnetwork.mixin;

import io.github.therealkamisama.miguelnetwork.client.ClientConnectionRedirect;
import java.net.InetAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.OldServerPinger;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OldServerPinger.class)
public abstract class OldServerPingerMixin {
    @Unique private final java.util.Map<ServerData, Boolean> miguelnetwork$discoveredQueries =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<ServerData, Boolean>());

    @Redirect(method = "func_147224_a(Lnet/minecraft/client/multiplayer/ServerData;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkManager;provideLanClient(Ljava/net/InetAddress;I)Lnet/minecraft/network/NetworkManager;"), require = 1)
    private NetworkManager miguelnetwork$rememberStatusRoute(InetAddress address, int port, ServerData server) {
        NetworkManager manager = NetworkManager.provideLanClient(address, port);
        miguelnetwork$discoveredQueries.put(server, manager.channel().attr(ClientConnectionRedirect.DISCOVERY).get());
        return manager;
    }

    @Inject(method = "func_147225_b(Lnet/minecraft/client/multiplayer/ServerData;)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void miguelnetwork$keepDiscoveredStatusOnItsRoute(ServerData server, CallbackInfo callback) {
        if (Boolean.TRUE.equals(miguelnetwork$discoveredQueries.remove(server))) {
            callback.cancel();
        }
    }
}
