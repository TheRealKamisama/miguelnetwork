package io.github.therealkamisama.miguelnetwork.core;

import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

public final class ManagedWstunnelProcess implements AutoCloseable {
    private final Process process;
    private final Thread outputThread;
    private final CompletableFuture<Void> ready;
    private final java.util.concurrent.CopyOnWriteArrayList<OutputSignal> signals;

    private ManagedWstunnelProcess(Process process, Thread outputThread, CompletableFuture<Void> ready,
                                  java.util.concurrent.CopyOnWriteArrayList<OutputSignal> signals) {
        this.process = process;
        this.outputThread = outputThread;
        this.ready = ready;
        this.signals = signals;
    }

    public static ManagedWstunnelProcess start(
            List<String> command,
            Predicate<String> readinessLine,
            Logger logger
    ) throws IOException {
        Objects.requireNonNull(command, "command");
        ProcessBuilder builder = new ProcessBuilder(command);
        // Clap treats the conventional NO_COLOR=1 value as an invalid boolean for this option in wstunnel v10.7.1.
        // We pass --no-color explicitly, so inherited values must not be allowed to break process startup.
        builder.environment().remove("NO_COLOR");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        CompletableFuture<Void> ready = new CompletableFuture<>();
        var signals = new java.util.concurrent.CopyOnWriteArrayList<OutputSignal>();
        Thread outputThread = new Thread(
                () -> consumeOutput(process, process.getInputStream(), readinessLine, ready, logger, signals),
                "MiguelNetwork-wstunnel-log-" + process.pid()
        );
        outputThread.setDaemon(true);
        outputThread.start();
        process.onExit().thenAccept(exited -> {
            signals.forEach(signal -> signal.future().completeExceptionally(
                    new IOException("wstunnel exited while waiting for output")));
            if (!ready.isDone()) {
                ready.completeExceptionally(new IOException("wstunnel exited before becoming ready: " + exited.exitValue()));
            }
        });
        return new ManagedWstunnelProcess(process, outputThread, ready, signals);
    }

    private static void consumeOutput(
            Process process,
            InputStream stream,
            Predicate<String> readinessLine,
            CompletableFuture<Void> ready,
            Logger logger,
            java.util.concurrent.CopyOnWriteArrayList<OutputSignal> signals
    ) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String safeLine = redact(line);
                logger.info("[wstunnel:{}] {}", process.pid(), safeLine);
                signals.forEach(signal -> {
                    if (signal.matches().test(safeLine)) signal.future().complete(null);
                });
                if (!ready.isDone() && readinessLine.test(line)) {
                    ready.complete(null);
                }
            }
        } catch (IOException exception) {
            if (process.isAlive()) {
                logger.warn("Cannot read wstunnel output", exception);
            }
        }
    }

    private static String redact(String line) {
        return line.replaceAll("(?i)(authorization|password|token)([=: ]+)[^ ,;]+", "$1$2<redacted>");
    }

    /** Register before editing restrictions so the reload acknowledgement cannot race the caller. */
    public CompletableFuture<Void> expectOutput(Predicate<String> matches) {
        var future = new CompletableFuture<Void>();
        var signal = new OutputSignal(matches, future);
        signals.add(signal);
        future.whenComplete((ignored, failure) -> signals.remove(signal));
        if (!process.isAlive()) future.completeExceptionally(new IOException("wstunnel is not running"));
        return future;
    }

    private record OutputSignal(Predicate<String> matches, CompletableFuture<Void> future) {}

    public void awaitReady(Duration timeout) throws IOException {
        awaitReady(timeout, () -> false);
    }

    public void awaitReady(Duration timeout, java.util.function.BooleanSupplier cancelled) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (true) {
                if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException("Connection cancelled");
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new TimeoutException();
                try {
                    ready.get(Math.min(100, Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining))), TimeUnit.MILLISECONDS);
                    return;
                } catch (TimeoutException waiting) {
                    if (System.nanoTime() >= deadline) throw waiting;
                }
            }
        } catch (java.util.concurrent.CancellationException cancelledFailure) {
            close();
            throw cancelledFailure;
        } catch (TimeoutException exception) {
            close();
            throw new IOException("Timed out waiting for wstunnel readiness", exception);
        } catch (InterruptedException exception) {
            close();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for wstunnel readiness", exception);
        } catch (Exception exception) {
            close();
            Throwable cause = exception.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("wstunnel failed during startup", cause == null ? exception : cause);
        }
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    @Override
    public void close() {
        process.destroy();
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(3, TimeUnit.SECONDS);
            }
            outputThread.join(1000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
