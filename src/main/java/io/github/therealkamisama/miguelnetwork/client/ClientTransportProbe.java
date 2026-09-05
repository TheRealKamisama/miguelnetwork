package io.github.therealkamisama.miguelnetwork.client;

import io.github.therealkamisama.miguelnetwork.core.EndpointMatcher;
import io.github.therealkamisama.miguelnetwork.core.TransportProtocol;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

final class ClientTransportProbe {
    private static final int MAX_RESPONSE_HEADER_BYTES = 16 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private ClientTransportProbe() {
    }

    static Optional<TransportProtocol> detect(
            String host,
            int publicPort,
            int targetPort,
            String pathPrefix,
            Duration timeout
    ) {
        if (probe(host, publicPort, targetPort, pathPrefix, timeout, TransportProtocol.WSS)) {
            return Optional.of(TransportProtocol.WSS);
        }
        if (probe(host, publicPort, targetPort, pathPrefix, timeout, TransportProtocol.WS)) {
            return Optional.of(TransportProtocol.WS);
        }
        return Optional.empty();
    }

    private static boolean probe(
            String host,
            int publicPort,
            int targetPort,
            String pathPrefix,
            Duration timeout,
            TransportProtocol transport
    ) {
        int timeoutMillis = Math.toIntExact(Math.max(1, Math.min(Integer.MAX_VALUE, timeout.toMillis())));
        try (Socket connected = connect(host, publicPort, timeoutMillis);
             Socket socket = transport.usesTls()
                     ? tls(connected, host, publicPort, timeoutMillis)
                     : connected) {
            return exchange(socket, host, publicPort, targetPort, pathPrefix);
        } catch (IOException | GeneralSecurityException | RuntimeException ignored) {
            return false;
        }
    }

    private static Socket connect(String host, int port, int timeoutMillis) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(stripIpv6Brackets(host), port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            socket.setTcpNoDelay(true);
            return socket;
        } catch (IOException exception) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            throw exception;
        }
    }

    private static SSLSocket tls(Socket connected, String host, int port, int timeoutMillis) throws IOException {
        String tlsHost = stripIpv6Brackets(host);
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = (SSLSocket) factory.createSocket(connected, tlsHost, port, true);
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        socket.setSSLParameters(parameters);
        socket.setSoTimeout(timeoutMillis);
        socket.startHandshake();
        return socket;
    }

    private static boolean exchange(
            Socket socket,
            String host,
            int publicPort,
            int targetPort,
            String pathPrefix
    ) throws IOException, GeneralSecurityException {
        byte[] websocketKeyBytes = new byte[16];
        RANDOM.nextBytes(websocketKeyBytes);
        String websocketKey = Base64.getEncoder().encodeToString(websocketKeyBytes);
        String jwt = createProbeJwt(targetPort);
        String request = "GET /" + pathPrefix + "/events HTTP/1.1\r\n"
                + "Host: " + EndpointMatcher.formatEndpoint(host, publicPort) + "\r\n"
                + "Connection: Upgrade\r\n"
                + "Upgrade: websocket\r\n"
                + "Sec-WebSocket-Key: " + websocketKey + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "Sec-WebSocket-Protocol: v1, authorization.bearer." + jwt + "\r\n"
                + "\r\n";

        OutputStream output = socket.getOutputStream();
        output.write(request.getBytes(StandardCharsets.ISO_8859_1));
        output.flush();

        String response = readHeaders(socket.getInputStream());
        if (!isWstunnelUpgrade(response, websocketKey)) {
            return false;
        }

        // Masked, empty WebSocket close frame. This closes the probe tunnel without sending Minecraft data.
        byte[] mask = new byte[4];
        RANDOM.nextBytes(mask);
        output.write(new byte[]{(byte) 0x88, (byte) 0x80, mask[0], mask[1], mask[2], mask[3]});
        output.flush();
        return true;
    }

    static String createProbeJwt(int targetPort) throws GeneralSecurityException {
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = "{\"id\":\"" + UUID.randomUUID()
                + "\",\"p\":{\"Tcp\":{\"proxy_protocol\":false}},\"r\":\"127.0.0.1\",\"rp\":"
                + targetPort + "}";
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String encodedHeader = encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signingInput = encodedHeader + "." + encodedPayload;
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        String signature = encoder.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
        return signingInput + "." + signature;
    }

    static boolean isWstunnelUpgrade(String response, String websocketKey) throws GeneralSecurityException {
        if (response == null || response.isEmpty()) {
            return false;
        }
        String[] lines = response.split("\\r?\\n");
        if (lines.length == 0 || !lines[0].matches("HTTP/1\\.[01] 101(?: .*)?")) {
            return false;
        }
        String expectedAccept = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1")
                        .digest((websocketKey + WEBSOCKET_GUID).getBytes(StandardCharsets.US_ASCII))
        );
        boolean accept = false;
        boolean protocol = false;
        for (int index = 1; index < lines.length; index++) {
            int separator = lines[index].indexOf(':');
            if (separator < 0) {
                continue;
            }
            String name = lines[index].substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = lines[index].substring(separator + 1).trim();
            if (name.equals("sec-websocket-accept") && MessageDigest.isEqual(
                    expectedAccept.getBytes(StandardCharsets.US_ASCII),
                    value.getBytes(StandardCharsets.US_ASCII))) {
                accept = true;
            } else if (name.equals("sec-websocket-protocol") && value.equalsIgnoreCase("v1")) {
                protocol = true;
            }
        }
        return accept && protocol;
    }

    private static String readHeaders(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int state = 0;
        while (buffer.size() < MAX_RESPONSE_HEADER_BYTES) {
            int value = input.read();
            if (value < 0) {
                break;
            }
            buffer.write(value);
            state = switch (state) {
                case 0 -> value == '\r' ? 1 : 0;
                case 1 -> value == '\n' ? 2 : value == '\r' ? 1 : 0;
                case 2 -> value == '\r' ? 3 : 0;
                case 3 -> value == '\n' ? 4 : 0;
                default -> state;
            };
            if (state == 4) {
                return buffer.toString(StandardCharsets.ISO_8859_1);
            }
        }
        throw new IOException("Incomplete or oversized HTTP response during MiguelNetwork transport discovery");
    }

    private static String stripIpv6Brackets(String host) {
        return host.length() > 1 && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']'
                ? host.substring(1, host.length() - 1)
                : host;
    }
}
