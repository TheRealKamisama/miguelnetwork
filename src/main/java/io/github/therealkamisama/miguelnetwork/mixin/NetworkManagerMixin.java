package io.github.therealkamisama.miguelnetwork.mixin;

import io.github.therealkamisama.miguelnetwork.client.ClientTunnelManager;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.net.InetAddress;
import java.net.InetSocketAddress;

@Mixin(NetworkManager.class)
public abstract class NetworkManagerMixin {
    /** Keep the caller's handshake address intact; change only the Netty socket destination. */
    @ModifyArgs(
            method = "createNetworkManagerAndConnect(Ljava/net/InetAddress;IZ)Lnet/minecraft/network/NetworkManager;",
            at = @At(value = "INVOKE",
                    target = "Lio/netty/bootstrap/Bootstrap;connect(Ljava/net/InetAddress;I)Lio/netty/channel/ChannelFuture;",
                    remap = false),
            require = 1, allow = 1)
    private static void miguelnetwork$redirectSocket(Args args) {
        InetAddress address = args.get(0);
        int port = args.get(1);
        // InetSocketAddress retains InetAddress's cached hostname without a reverse DNS lookup.
        InetSocketAddress redirected = ClientTunnelManager.redirect(new InetSocketAddress(address, port));
        args.set(0, redirected.getAddress());
        args.set(1, redirected.getPort());
    }
}
