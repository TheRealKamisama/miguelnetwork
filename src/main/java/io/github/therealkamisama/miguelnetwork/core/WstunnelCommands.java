package io.github.therealkamisama.miguelnetwork.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class WstunnelCommands {
    private WstunnelCommands() {
    }

    public static List<String> client(
            Path executable,
            String host,
            int publicPort,
            int localPort,
            int targetPort,
            String pathPrefix,
            TransportProtocol transport,
            boolean verifyCertificate
    ) {
        List<String> command = base(executable, 1);
        command.add("client");
        if (transport.usesTls() && verifyCertificate) {
            command.add("--tls-verify-certificate");
        }
        command.add("--connection-retry-max-backoff");
        command.add("10s");
        command.add("--websocket-ping-frequency");
        command.add("30s");
        command.add("--http-upgrade-path-prefix");
        command.add(pathPrefix);
        command.add("-L");
        command.add("tcp://127.0.0.1:" + localPort + ":127.0.0.1:" + targetPort);
        command.add(transport.scheme() + "://" + formatHost(host) + ":" + publicPort);
        return command;
    }

    public static List<String> server(
            Path executable,
            String bindHost,
            int publicPort,
            Path restrictions,
            TransportProtocol transport,
            Path certificate,
            Path privateKey
    ) {
        List<String> command = base(executable, 2);
        command.add("server");
        command.add("--restrict-config");
        command.add(restrictions.toString());
        if ((certificate == null) != (privateKey == null)) {
            throw new IllegalArgumentException("TLS certificate and private key must either both be set or both be absent");
        }
        if (!transport.usesTls() && certificate != null) {
            throw new IllegalArgumentException("TLS certificate and private key cannot be used with WS transport");
        }
        if (certificate != null) {
            command.add("--tls-certificate");
            command.add(certificate.toString());
            command.add("--tls-private-key");
            command.add(privateKey.toString());
        }
        command.add(transport.scheme() + "://" + formatHost(bindHost) + ":" + publicPort);
        return command;
    }

    private static List<String> base(Path executable, int workerThreads) {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.add("--no-color");
        command.add("--log-lvl");
        command.add("INFO");
        command.add("--nb-worker-threads");
        command.add(Integer.toString(workerThreads));
        return command;
    }

    private static String formatHost(String host) {
        return host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;
    }
}
