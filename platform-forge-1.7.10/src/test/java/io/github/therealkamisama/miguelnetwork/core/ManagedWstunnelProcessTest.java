package io.github.therealkamisama.miguelnetwork.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManagedWstunnelProcessTest {
    @Test void readinessAndCloseReapTheChild() throws Exception {
        ManagedWstunnelProcess process = launch("ready");
        try (process) {
            process.awaitReady(Duration.ofSeconds(10));
            assertTrue(process.isAlive());
        }
        assertFalse(process.isAlive());
    }

    @Test void earlyExitFailsReadinessAndReapsTheChild() throws Exception {
        try (var process = launch("exit")) {
            var error = assertThrows(java.io.IOException.class, () -> process.awaitReady(Duration.ofSeconds(10)));
            assertTrue(error.getMessage().contains("exited before becoming ready: 17"));
            assertFalse(process.isAlive());
        }
    }

    @Test void timeoutClosesTheUnreadyChild() throws Exception {
        try (var process = launch("silent")) {
            assertThrows(java.io.IOException.class, () -> process.awaitReady(Duration.ofMillis(300)));
            assertFalse(process.isAlive());
        }
    }

    @Test void interruptedReadinessPreservesInterruptAndClosesChild() throws Exception {
        try (var process = launch("silent")) {
            Thread.currentThread().interrupt();
            try {
                assertThrows(java.io.IOException.class, () -> process.awaitReady(Duration.ofSeconds(10)));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally { Thread.interrupted(); }
            assertFalse(process.isAlive());
        }
    }

    private static ManagedWstunnelProcess launch(String mode) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", OperatingSystem.current().executableName().endsWith(".exe") ? "java.exe" : "java");
        String classpath = Path.of(ManagedProcessFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        return ManagedWstunnelProcess.start(List.of(java.toString(), "-cp", classpath,
                ManagedProcessFixture.class.getName(), mode), "TEST_READY"::equals, LogManager.getLogger("ProcessTest"));
    }
}
