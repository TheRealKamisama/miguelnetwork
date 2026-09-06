package io.github.therealkamisama.miguelnetwork.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;

class ServerTunnelControllerTest {
    @Test
    void generatedRestrictionsOnlyAllowMinecraftTcpLoopback() {
        String yaml = ServerTunnelController.restrictions("miguelnetwork-v1", List.of(25566, 25565));
        assertTrue(yaml.contains("- Tcp"));
        assertTrue(yaml.contains("\"25566\""));
        assertTrue(yaml.contains("\"25565\""));
        assertTrue(yaml.contains("127.0.0.1/32"));
        assertTrue(yaml.contains("::1/128"));
        assertFalse(yaml.contains("ReverseTunnel"));
        assertFalse(yaml.contains("Udp"));
    }
}
