package io.github.therealkamisama.miguelnetwork.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class ClientConnectionRedirect {
    public static final io.netty.util.AttributeKey<Boolean> ROUTED = new io.netty.util.AttributeKey<>("miguelnetwork.routed");
    public static final io.netty.util.AttributeKey<Boolean> DISCOVERY = new io.netty.util.AttributeKey<>("miguelnetwork.discovery");
    private ClientConnectionRedirect() { }

    public static ChannelFuture connect(Bootstrap bootstrap, InetAddress address, int port) {
        // InetSocketAddress.getHostString retains the caller's DNS/SRV hostname without reverse DNS.
        // GuiConnecting and OldServerPinger retain their own original handshake arguments.
        var attempt = ClientConnectionAttempt.take();
        if (attempt != null) attempt.checkCancelled();
        var lease = ClientTunnelManager.acquire(new InetSocketAddress(address, port));
        try {
            if (attempt != null) attempt.checkCancelled();
            ChannelFuture future = bootstrap.connect(lease.address());
            if (lease.tunneled()) future.channel().attr(ROUTED).set(Boolean.TRUE);
            if (lease.discovered()) future.channel().attr(DISCOVERY).set(Boolean.TRUE);
            // Process reaping must not hold up Netty's close notification or other connections.
            future.channel().closeFuture().addListener((ChannelFutureListener) closed ->
                    Thread.ofVirtual().name("MiguelNetwork-release").start(lease::close));
            future.addListener((ChannelFutureListener) connected -> {
                if (!connected.isSuccess()) lease.close();
            });
            if (attempt != null) attempt.bind(future.channel());
            return afterChannelActivation(future);
        } catch (RuntimeException | Error exception) {
            lease.close();
            throw exception;
        }
    }

    static ChannelFuture afterChannelActivation(ChannelFuture connected) {
        ChannelPromise initialized = connected.channel().newPromise();
        connected.addListener((ChannelFutureListener) result -> {
            if (!result.isSuccess()) {
                initialized.tryFailure(result.cause() == null
                        ? new java.util.concurrent.CancellationException("Connection cancelled") : result.cause());
                return;
            }
            // Netty 4.0 marks connect successful before propagating channelActive.
            // The next event-loop task runs after that propagation has initialized
            // Minecraft's channel field and handshake state.
            result.channel().eventLoop().execute(() -> {
                if (result.channel().isActive()) initialized.trySuccess();
                else initialized.tryFailure(new java.nio.channels.ClosedChannelException());
            });
        });
        return initialized;
    }
}
