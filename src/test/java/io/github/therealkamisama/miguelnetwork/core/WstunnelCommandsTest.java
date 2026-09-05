package io.github.therealkamisama.miguelnetwork.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WstunnelCommandsTest {
    @Test
    void clientForcesTlsVerificationAndLoopbackTarget() {
        List<String> command = WstunnelCommands.client(
                Path.of("wstunnel"), "mc.example.com", 25565, 49152, 25566, "miguelnetwork-v1", true
        );
        assertTrue(command.contains("--tls-verify-certificate"));
        assertTrue(command.contains("tcp://127.0.0.1:49152:127.0.0.1:25566"));
        assertEquals("wss://mc.example.com:25565", command.get(command.size() - 1));
    }

    @Test
    void ipv6HostsAreBracketed() {
        List<String> command = WstunnelCommands.client(
                Path.of("wstunnel"), "2001:db8::1", 443, 49152, 25566, "miguelnetwork-v1", true
        );
        assertEquals("wss://[2001:db8::1]:443", command.get(command.size() - 1));
    }

    @Test
    void clientCanDisableCertificateVerificationOnlyWhenExplicitlyRequested() {
        List<String> command = WstunnelCommands.client(
                Path.of("wstunnel"), "127.0.0.1", 25565, 49152, 25566, "miguelnetwork-v1", false
        );
        assertTrue(!command.contains("--tls-verify-certificate"));
    }

    @Test
    void serverCanUseWstunnelBuiltInCertificate() {
        List<String> command = WstunnelCommands.server(
                Path.of("wstunnel"), "127.0.0.1", 25565, Path.of("restrictions.yaml"), null, null
        );
        assertTrue(!command.contains("--tls-certificate"));
        assertTrue(!command.contains("--tls-private-key"));
        assertEquals("wss://127.0.0.1:25565", command.get(command.size() - 1));
    }
}
