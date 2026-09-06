package io.github.therealkamisama.miguelnetwork.client;

import io.github.therealkamisama.miguelnetwork.core.EndpointMatcher;
import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryCodec;
import io.github.therealkamisama.miguelnetwork.discovery.DiscoveryManifest;
import io.github.therealkamisama.miguelnetwork.discovery.SignedDiscoveryDocument;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

final class ClientDiscoveryService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder NONCE_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final long CLOCK_SKEW_SECONDS = 30;
    private final HttpClient httpClient;
    private final ClientTrustStore trustStore;

    ClientDiscoveryService(ClientTrustStore trustStore, Duration connectTimeout) {
        this(HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), trustStore);
    }

    ClientDiscoveryService(HttpClient httpClient, ClientTrustStore trustStore) {
        this.httpClient = httpClient;
        this.trustStore = trustStore;
    }

    DiscoveryManifest discover(String host, int port, Duration timeout) throws IOException, GeneralSecurityException {
        String audience = EndpointMatcher.formatEndpoint(host, port).toLowerCase();
        byte[] nonceBytes = new byte[32];
        RANDOM.nextBytes(nonceBytes);
        String nonce = NONCE_ENCODER.encodeToString(nonceBytes);
        URI uri = URI.create("https://" + EndpointMatcher.formatEndpoint(host, port)
                + MiguelNetworkProtocol.DISCOVERY_PATH);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofByteArray(DiscoveryCodec.encodeRequest(nonce)))
                .build();
        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while discovering " + audience, exception);
        }
        if (response.statusCode() != 200) {
            throw new IOException("Discovery endpoint returned HTTP " + response.statusCode());
        }
        if (response.body().length > DiscoveryCodec.MAX_DOCUMENT_BYTES) {
            throw new IOException("Discovery document is oversized");
        }
        SignedDiscoveryDocument document = DiscoveryCodec.decodeAndVerify(response.body());
        DiscoveryManifest manifest = document.manifest();
        if (!MiguelNetworkProtocol.DISCOVERY_PROTOCOL.equals(manifest.protocol())) {
            throw new GeneralSecurityException("Unsupported Discovery protocol " + manifest.protocol());
        }
        if (!audience.equals(manifest.audience().toLowerCase())) {
            throw new GeneralSecurityException("Discovery audience mismatch");
        }
        if (!nonce.equals(manifest.clientNonce())) {
            throw new GeneralSecurityException("Discovery nonce mismatch");
        }
        if (!manifest.isValidAt(Instant.now(), CLOCK_SKEW_SECONDS)) {
            throw new GeneralSecurityException("Discovery manifest is expired or not yet valid");
        }
        trustStore.accept(audience, document);
        return manifest;
    }
}
