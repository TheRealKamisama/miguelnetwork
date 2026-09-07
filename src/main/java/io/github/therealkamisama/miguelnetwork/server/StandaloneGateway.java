package io.github.therealkamisama.miguelnetwork.server;

import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class StandaloneGateway implements AutoCloseable {
    private static final int MAX_HEADER_BYTES = 32 * 1024;
    private static final int MAX_DISCOVERY_BODY_BYTES = 4096;
    private static final int HEADER_TIMEOUT_MILLIS = 10_000;
    private static final int UPSTREAM_CONNECT_TIMEOUT_MILLIS = 5_000;

    private final ServerSocket listener;
    private final String upstreamHost;
    private final int upstreamPort;
    private final DiscoveryHandler discoveryHandler;
    private final ExecutorService connections;
    private final Set<Socket> activeSockets = ConcurrentHashMap.newKeySet();
    private final Thread acceptThread;
    private volatile boolean running = true;

    private StandaloneGateway(
            ServerSocket listener,
            String upstreamHost,
            int upstreamPort,
            DiscoveryHandler discoveryHandler
    ) {
        this.listener = listener;
        this.upstreamHost = upstreamHost;
        this.upstreamPort = upstreamPort;
        this.discoveryHandler = discoveryHandler;
        this.connections = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("MiguelNetwork-gateway-connection-", 0).factory());
        this.acceptThread = Thread.ofPlatform().daemon().name("MiguelNetwork-gateway-accept").unstarted(this::acceptLoop);
    }

    static StandaloneGateway start(
            String bindHost,
            int publicPort,
            String upstreamHost,
            int upstreamPort,
            DiscoveryHandler discoveryHandler
    ) throws IOException {
        ServerSocket listener = new ServerSocket();
        listener.setReuseAddress(true);
        listener.bind(new InetSocketAddress(bindHost, publicPort), 128);
        StandaloneGateway result = new StandaloneGateway(
                listener, upstreamHost, upstreamPort, discoveryHandler);
        result.acceptThread.start();
        return result;
    }

    int localPort() {
        return listener.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = listener.accept();
                configure(client);
                activeSockets.add(client);
                connections.submit(() -> handle(client));
            } catch (SocketException exception) {
                if (running) {
                    MiguelNetwork.LOGGER.warn("MiguelNetwork standalone gateway accept failed", exception);
                }
            } catch (IOException exception) {
                if (running) {
                    MiguelNetwork.LOGGER.warn("MiguelNetwork standalone gateway accept failed", exception);
                }
            }
        }
    }

    private void handle(Socket client) {
        try (client; BufferedInputStream input = new BufferedInputStream(client.getInputStream())) {
            byte[] headerBytes = readHttpHeader(input);
            RequestHead request = RequestHead.parse(headerBytes);
            if (discoveryHandler != null && request.path().equals(MiguelNetworkProtocol.DISCOVERY_PATH)) {
                handleDiscovery(client.getOutputStream(), input, request);
            } else {
                proxy(client, input, headerBytes);
            }
        } catch (Exception exception) {
            if (running && !(exception instanceof EOFException)) {
                MiguelNetwork.LOGGER.debug("MiguelNetwork standalone gateway connection closed: {}",
                        exception.toString());
            }
        } finally {
            activeSockets.remove(client);
        }
    }

    private void handleDiscovery(OutputStream output, InputStream input, RequestHead request) throws Exception {
        if (!request.method().equals("POST")) {
            sendHttp(output, 405, "Method Not Allowed", "text/plain; charset=utf-8",
                    "method not allowed\n".getBytes(StandardCharsets.UTF_8), "Allow: POST\r\n");
            return;
        }
        if (request.contentLength() < 0 || request.contentLength() > MAX_DISCOVERY_BODY_BYTES) {
            sendHttp(output, 400, "Bad Request", "text/plain; charset=utf-8",
                    "invalid discovery request\n".getBytes(StandardCharsets.UTF_8), "");
            return;
        }
        byte[] body = input.readNBytes(request.contentLength());
        if (body.length != request.contentLength()) {
            throw new EOFException("Incomplete Discovery request body");
        }
        try {
            byte[] response = discoveryHandler.respond(request.host(), body);
            sendHttp(output, 200, "OK", "application/json; charset=utf-8", response,
                    "Cache-Control: no-store\r\n");
        } catch (IllegalArgumentException exception) {
            sendHttp(output, 400, "Bad Request", "text/plain; charset=utf-8",
                    "invalid discovery request\n".getBytes(StandardCharsets.UTF_8), "");
        } catch (Exception exception) {
            MiguelNetwork.LOGGER.warn("Cannot answer MiguelNetwork Discovery request", exception);
            sendHttp(output, 500, "Internal Server Error", "text/plain; charset=utf-8",
                    "discovery unavailable\n".getBytes(StandardCharsets.UTF_8), "");
        }
    }

    private void proxy(Socket client, InputStream clientInput, byte[] initialHeader) throws Exception {
        try (Socket upstream = new Socket()) {
            configure(upstream);
            activeSockets.add(upstream);
            upstream.connect(new InetSocketAddress(upstreamHost, upstreamPort), UPSTREAM_CONNECT_TIMEOUT_MILLIS);
            client.setSoTimeout(0);
            upstream.setSoTimeout(0);
            OutputStream upstreamOutput = upstream.getOutputStream();
            upstreamOutput.write(initialHeader);
            upstreamOutput.flush();
            var reverse = connections.submit(() -> copy(upstream, client));
            try {
                clientInput.transferTo(upstreamOutput);
                shutdownOutput(upstream);
                reverse.get();
            } finally {
                upstream.close();
                client.close();
                reverse.cancel(true);
                activeSockets.remove(upstream);
            }
        }
    }

    private static void copy(Socket from, Socket to) {
        try {
            from.getInputStream().transferTo(to.getOutputStream());
            shutdownOutput(to);
        } catch (IOException ignored) {
        }
    }

    private static byte[] readHttpHeader(InputStream input) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream(1024);
        int matched = 0;
        while (result.size() < MAX_HEADER_BYTES) {
            int value = input.read();
            if (value < 0) {
                throw new EOFException("Connection ended before HTTP headers");
            }
            result.write(value);
            matched = switch (matched) {
                case 0 -> value == '\r' ? 1 : 0;
                case 1 -> value == '\n' ? 2 : value == '\r' ? 1 : 0;
                case 2 -> value == '\r' ? 3 : 0;
                case 3 -> value == '\n' ? 4 : 0;
                default -> 4;
            };
            if (matched == 4) {
                return result.toByteArray();
            }
        }
        throw new IOException("HTTP headers exceed " + MAX_HEADER_BYTES + " bytes");
    }

    private static void sendHttp(
            OutputStream output,
            int status,
            String reason,
            String contentType,
            byte[] body,
            String additionalHeaders
    ) throws IOException {
        String header = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + additionalHeaders + "\r\n";
        output.write(header.getBytes(StandardCharsets.ISO_8859_1));
        output.write(body);
        output.flush();
    }

    private static void configure(Socket socket) throws SocketException {
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        socket.setSoTimeout(HEADER_TIMEOUT_MILLIS);
    }

    private static void shutdownOutput(Socket socket) {
        try {
            socket.shutdownOutput();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            listener.close();
        } catch (IOException ignored) {
        }
        for (Socket socket : activeSockets) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        activeSockets.clear();
        connections.shutdownNow();
        try {
            acceptThread.join(2_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    interface DiscoveryHandler {
        byte[] respond(String audience, byte[] request) throws Exception;
    }

    private record RequestHead(String method, String path, String host, int contentLength) {
        private static RequestHead parse(byte[] bytes) {
            String text = new String(bytes, StandardCharsets.ISO_8859_1);
            String[] lines = text.split("\\r\\n");
            String[] requestLine = lines[0].split(" ", 3);
            if (requestLine.length != 3) {
                throw new IllegalArgumentException("Malformed HTTP request line");
            }
            Map<String, String> headers = new HashMap<>();
            for (int index = 1; index < lines.length; index++) {
                int separator = lines[index].indexOf(':');
                if (separator > 0) {
                    headers.putIfAbsent(lines[index].substring(0, separator).trim().toLowerCase(Locale.ROOT),
                            lines[index].substring(separator + 1).trim());
                }
            }
            String target = requestLine[1];
            String path = target.startsWith("http://") || target.startsWith("https://")
                    ? URI.create(target).getPath() : target.split("\\?", 2)[0];
            String host = headers.getOrDefault("host", "");
            int contentLength = headers.containsKey("content-length")
                    ? Integer.parseInt(headers.get("content-length")) : 0;
            return new RequestHead(requestLine[0].toUpperCase(Locale.ROOT), path, host, contentLength);
        }
    }
}
