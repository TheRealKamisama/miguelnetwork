package io.github.therealkamisama.miguelnetwork.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class ClientConnectionActivationTest {
    @Test void connectionRefusalCompletesTheExposedFutureWithFailure() throws Exception {
        int port;
        try (ServerSocket listener = new ServerSocket(0)) { port = listener.getLocalPort(); }
        var eventLoop = new NioEventLoopGroup(1);
        try {
            var connected = new Bootstrap().group(eventLoop).channel(NioSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter()).connect("127.0.0.1", port);
            var initialized = ClientConnectionRedirect.afterChannelActivation(connected);
            assertTrue(initialized.await(5, TimeUnit.SECONDS));
            assertFalse(initialized.isSuccess());
            assertNotNull(initialized.cause());
        } finally { eventLoop.shutdownGracefully().sync(); }
    }

    @Test void connectIsNotExposedUntilMinecraftChannelActivationCompletes() throws Exception {
        var eventLoop = new NioEventLoopGroup(1);
        var entered = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        var activated = new AtomicBoolean();
        try (ServerSocket listener = new ServerSocket(0)) {
            var connected = new Bootstrap().group(eventLoop).channel(NioSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override public void channelActive(ChannelHandlerContext context) throws Exception {
                            entered.countDown();
                            if (!proceed.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test activation timeout");
                            activated.set(true);
                            super.channelActive(context);
                        }
                    }).connect("127.0.0.1", listener.getLocalPort());
            try (var accepted = listener.accept()) {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertTrue(connected.isSuccess(), "Original Netty future completes before channelActive finishes");
                var initialized = ClientConnectionRedirect.afterChannelActivation(connected);
                assertFalse(initialized.isDone());
                assertFalse(activated.get());
                proceed.countDown();
                assertTrue(initialized.await(5, TimeUnit.SECONDS));
                assertTrue(initialized.isSuccess());
                assertTrue(activated.get());
                connected.channel().close().sync();
            }
        } finally {
            proceed.countDown();
            eventLoop.shutdownGracefully().sync();
        }
    }
}
