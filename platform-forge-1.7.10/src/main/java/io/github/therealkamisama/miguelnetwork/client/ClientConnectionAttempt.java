package io.github.therealkamisama.miguelnetwork.client;

import io.netty.channel.Channel;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Carries cancellation from the connection screen to its asynchronous socket attempt. */
public final class ClientConnectionAttempt {
    // Weak keys also release attempts whose DNS lookup fails before reaching NetworkManager.
    private static final Map<Thread, ClientConnectionAttempt> ATTEMPTS = new WeakHashMap<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicReference<Channel> channel = new AtomicReference<>();

    public static synchronized ClientConnectionAttempt register(Thread thread) {
        var attempt = new ClientConnectionAttempt();
        ATTEMPTS.put(thread, attempt);
        return attempt;
    }

    static synchronized ClientConnectionAttempt take() { return ATTEMPTS.remove(Thread.currentThread()); }

    public void cancel() {
        cancelled.set(true);
        Channel current = channel.get();
        if (current != null) current.close();
    }

    public boolean cancelled() { return cancelled.get(); }

    void checkCancelled() {
        if (cancelled.get()) throw new CancellationException("MiguelNetwork connection cancelled");
    }

    void bind(Channel connected) {
        channel.set(connected);
        if (cancelled.get()) connected.close();
    }
}
