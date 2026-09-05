package io.github.therealkamisama.miguelnetwork.server;

import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import io.github.therealkamisama.miguelnetwork.core.ManagedWstunnelProcess;
import io.github.therealkamisama.miguelnetwork.core.NativeWstunnel;
import io.github.therealkamisama.miguelnetwork.core.WstunnelCommands;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class ServerTunnelController {
    private static ManagedWstunnelProcess process;

    private ServerTunnelController() {
    }

    public static synchronized void start(MinecraftServer server) {
        if (!Boolean.parseBoolean(System.getProperty("miguelnetwork.server.enabled", "false"))) {
            MiguelNetwork.LOGGER.info("MiguelNetwork server tunnel is disabled for this technical-validation run");
            return;
        }
        stop();
        try {
            int publicPort = Integer.getInteger("miguelnetwork.public.port", 25565);
            int targetPort = Integer.getInteger("miguelnetwork.target.port", 25566);
            String bindHost = System.getProperty("miguelnetwork.bindHost", "0.0.0.0");
            String pathPrefix = System.getProperty("miguelnetwork.pathPrefix", "miguelnetwork-v1");
            Path gameDirectory = FMLPaths.GAMEDIR.get();
            Path executable = NativeWstunnel.resolve(gameDirectory);
            Path generated = gameDirectory.resolve("config/miguelnetwork/generated/restrictions.yaml");
            Files.createDirectories(generated.getParent());
            Files.write(generated, restrictions(pathPrefix, targetPort).getBytes(StandardCharsets.UTF_8));

            Path certificate;
            Path privateKey;
            String certificateValue = System.getProperty("miguelnetwork.tls.certificate", "").trim();
            String privateKeyValue = System.getProperty("miguelnetwork.tls.privateKey", "").trim();
            if (certificateValue.isEmpty() && privateKeyValue.isEmpty()) {
                boolean allowBuiltInSelfSigned = Boolean.parseBoolean(
                        System.getProperty("miguelnetwork.tls.allowBuiltInSelfSigned", "false")
                );
                if (!allowBuiltInSelfSigned) {
                    throw new IOException("Missing TLS certificate and private key. For local development only, set "
                            + "-Dmiguelnetwork.tls.allowBuiltInSelfSigned=true");
                }
                certificate = null;
                privateKey = null;
                MiguelNetwork.LOGGER.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                MiguelNetwork.LOGGER.warn("MiguelNetwork IS USING WSTUNNEL'S BUILT-IN SELF-SIGNED CERTIFICATE (DEVELOPMENT ONLY)");
                MiguelNetwork.LOGGER.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            } else if (certificateValue.isEmpty() || privateKeyValue.isEmpty()) {
                throw new IOException("Both -Dmiguelnetwork.tls.certificate and -Dmiguelnetwork.tls.privateKey are required");
            } else {
                certificate = requiredPath("miguelnetwork.tls.certificate", certificateValue);
                privateKey = requiredPath("miguelnetwork.tls.privateKey", privateKeyValue);
            }
            process = ManagedWstunnelProcess.start(
                    WstunnelCommands.server(executable, bindHost, publicPort, generated, certificate, privateKey),
                    line -> line.contains("Starting wstunnel server listening on"),
                    MiguelNetwork.LOGGER
            );
            process.awaitReady(Duration.ofSeconds(10));
            MiguelNetwork.LOGGER.info("MiguelNetwork WSS listener is ready on {}:{} -> 127.0.0.1:{}",
                    bindHost, publicPort, targetPort);
        } catch (Exception exception) {
            stop();
            MiguelNetwork.LOGGER.error("MiguelNetwork server tunnel failed to start", exception);
        }
    }

    private static Path requiredPath(String property, String value) throws IOException {
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IOException("Configured file does not exist: " + path);
        }
        return path;
    }

    static String restrictions(String pathPrefix, int targetPort) {
        String escapedPrefix = pathPrefix.replace("\\", "\\\\").replace("\"", "\\\"");
        return "restrictions:\n"
                + "  - name: \"MiguelNetwork Minecraft only\"\n"
                + "    description: \"Only TCP forwarding to the loopback Minecraft listener\"\n"
                + "    match:\n"
                + "      - !PathPrefix \"^" + escapedPrefix + "$\"\n"
                + "    allow:\n"
                + "      - !Tunnel\n"
                + "        protocol:\n"
                + "          - Tcp\n"
                + "        port:\n"
                + "          - \"" + targetPort + "\"\n"
                + "        host: \"^$\"\n"
                + "        cidr:\n"
                + "          - \"127.0.0.1/32\"\n"
                + "          - \"::1/128\"\n";
    }

    public static synchronized void stop() {
        if (process != null) {
            process.close();
            process = null;
        }
    }
}
