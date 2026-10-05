package io.github.therealkamisama.miguelnetwork.server;

import io.github.therealkamisama.miguelnetwork.config.DeploymentMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerEndpointResolverTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsServerIpAndRecordsRuntimeAndZstdNetPorts() throws Exception {
        Files.writeString(temporaryDirectory.resolve("server.properties"),
                "server-ip=192.168.0.146\nserver-port=25567\n");
        var resolved = ServerEndpointResolver.resolve(temporaryDirectory, 25567, OptionalInt.of(25566));

        assertEquals("192.168.0.146", resolved.targetHost());
        assertEquals(25567, resolved.minecraftPort());
        assertEquals(25566, resolved.zstdNetPort());

        ServerEndpointResolver.writeDetectedConfiguration(
                temporaryDirectory, resolved, DeploymentMode.STANDALONE, "0.0.0.0", 35548);
        String generated = Files.readString(temporaryDirectory.resolve(
                "config/miguelnetwork/generated/detected-server.toml"));
        assertTrue(generated.contains("minecraftTargetHost = \"192.168.0.146\""));
        assertTrue(generated.contains("minecraftTargetPort = 25567"));
        assertTrue(generated.contains("zstdNetTargetPort = 25566"));
    }

    @Test
    void wildcardServerIpUsesLoopbackForTheLocalSidecar() throws Exception {
        Files.writeString(temporaryDirectory.resolve("server.properties"), "server-ip=\nserver-port=25565\n");
        var resolved = ServerEndpointResolver.resolve(temporaryDirectory, 25565, OptionalInt.empty());

        assertEquals("127.0.0.1", resolved.targetHost());
    }
}
