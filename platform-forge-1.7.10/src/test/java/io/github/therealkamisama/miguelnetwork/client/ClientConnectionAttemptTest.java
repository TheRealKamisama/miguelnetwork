package io.github.therealkamisama.miguelnetwork.client;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientConnectionAttemptTest {
    @Test void cancellationBeforeSocketCreationIsObserved() {
        var attempt = ClientConnectionAttempt.register(Thread.currentThread());
        assertSame(attempt, ClientConnectionAttempt.take());
        assertNull(ClientConnectionAttempt.take());
        attempt.cancel();
        assertThrows(java.util.concurrent.CancellationException.class, attempt::checkCancelled);
        var channel = new EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter());
        attempt.bind(channel);
        assertFalse(channel.isOpen());
    }

    @Test void cancellationAfterSocketCreationClosesIt() {
        var attempt = ClientConnectionAttempt.register(Thread.currentThread());
        ClientConnectionAttempt.take();
        var channel = new EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter());
        attempt.bind(channel);
        assertTrue(channel.isOpen());
        attempt.cancel();
        assertFalse(channel.isOpen());
    }
}
