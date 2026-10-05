package io.github.therealkamisama.miguelnetwork.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.therealkamisama.miguelnetwork.MiguelNetwork;
import io.github.therealkamisama.miguelnetwork.config.ServerConfig;
import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class DiscoveryHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;
    private final DiscoveryDocumentProvider documents;

    private DiscoveryHttpServer(HttpServer server, ExecutorService executor, DiscoveryDocumentProvider documents) {
        this.server = server;
        this.executor = executor;
        this.documents = documents;
    }

    static DiscoveryHttpServer start(DiscoveryDocumentProvider documents) throws IOException {
        InetSocketAddress address = new InetSocketAddress(ServerConfig.discoveryBindHost(), ServerConfig.discoveryPort());
        HttpServer httpServer = HttpServer.create(address, 16);
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "MiguelNetwork-discovery-http");
            thread.setDaemon(true);
            return thread;
        });
        DiscoveryHttpServer result = new DiscoveryHttpServer(httpServer, executor, documents);
        httpServer.createContext(MiguelNetworkProtocol.DISCOVERY_PATH, result::handle);
        httpServer.setExecutor(executor);
        httpServer.start();
        return result;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestURI().getPath().equals(MiguelNetworkProtocol.DISCOVERY_PATH)) {
                send(exchange, 404, "not found\n".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.getResponseHeaders().set("Allow", "POST");
                send(exchange, 405, "method not allowed\n".getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] body = exchange.getRequestBody().readNBytes(4097);
            String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-Host");
            String host = forwarded == null || forwarded.isBlank()
                    ? exchange.getRequestHeaders().getFirst("Host") : forwarded.split(",", 2)[0].trim();
            byte[] response = documents.response(host, body);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            send(exchange, 200, response);
        } catch (IllegalArgumentException exception) {
            sendIfPossible(exchange, 400, "invalid discovery request\n");
        } catch (Exception exception) {
            MiguelNetwork.LOGGER.warn("Cannot answer MiguelNetwork Discovery request", exception);
            sendIfPossible(exchange, 500, "discovery unavailable\n");
        } finally {
            exchange.close();
        }
    }

    private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static void sendIfPossible(HttpExchange exchange, int status, String message) {
        try {
            send(exchange, status, message.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
