package io.github.therealkamisama.miguelnetwork.mixin;

import io.github.therealkamisama.miguelnetwork.client.ClientConnectionRedirect;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import java.net.InetAddress;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import io.netty.channel.Channel;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ChatComponentTranslation;

@Mixin(NetworkManager.class)
public abstract class NetworkManagerMixin {
    @Shadow private Channel channel;

    // 1.7.10's status pinger discards a closed manager before checking its reason.
    // channelInactive can arrive after closeFuture, and closeChannel only stores a
    // reason while the channel is still open. Supply EOF for that routed-only race.
    @Inject(method = "getExitMessage()Lnet/minecraft/util/IChatComponent;", at = @At("RETURN"), cancellable = true)
    private void miguelnetwork$closedRouteReason(CallbackInfoReturnable<IChatComponent> callback) {
        if (callback.getReturnValue() == null && channel != null && !channel.isOpen()
                && Boolean.TRUE.equals(channel.attr(ClientConnectionRedirect.ROUTED).get())) {
            callback.setReturnValue(new ChatComponentTranslation("disconnect.endOfStream"));
        }
    }
    // Verified against Forge 1614 / stable 12. Only the TCP socket destination changes.
    @Redirect(method = "provideLanClient(Ljava/net/InetAddress;I)Lnet/minecraft/network/NetworkManager;",
            at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/Bootstrap;connect(Ljava/net/InetAddress;I)Lio/netty/channel/ChannelFuture;", remap = false),
            require = 1)
    private static ChannelFuture miguelnetwork$connect(Bootstrap bootstrap, InetAddress address, int port) {
        return ClientConnectionRedirect.connect(bootstrap, address, port);
    }
}
