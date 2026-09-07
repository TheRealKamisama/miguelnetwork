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
    private final boolean verifySignatures;

    ClientDiscoveryService(ClientTrustStore trustStore, Duration connectTimeout, boolean verifySignatures) {
        this(HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), trustStore, verifySignatures);
    }

    ClientDiscoveryService(HttpClient httpClient, ClientTrustStore trustStore, boolean verifySignatures) {
        this.httpClient = httpClient;
        this.trustStore = trustStore;
        this.verifySignatures = verifySignatures;
    }

    DiscoveryManifest discover(String host, int port, Duration timeout) throws IOException, GeneralSecurityException {
        String audience = EndpointMatcher.formatEndpoint(host, port).toLowerCase();
        byte[] nonceBytes = new byte[32];
        RANDOM.nextBytes(nonceBytes);
        String nonce = NONCE_ENCODER.encodeToString(nonceBytes);
        HttpResponse<byte[]> response = request(host, port, nonce, timeout, audience);
        if (response.statusCode() != 200) {
            throw new IOException("Discovery endpoint returned HTTP " + response.statusCode());
        }
        if (response.body().length > DiscoveryCodec.MAX_DOCUMENT_BYTES) {
            throw new IOException("Discovery document is oversized");
        }
        SignedDiscoveryDocument signed = verifySignatures
                ? DiscoveryCodec.decodeAndVerify(response.body()) : null;
        DiscoveryManifest manifest = signed == null
                ? DiscoveryCodec.decodeWithoutSignatureVerification(response.body()) : signed.manifest();
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
        if (signed != null) {
            trustStore.accept(audience, signed);
        }
        return manifest;
    }

    private HttpResponse<byte[]> request(
            String host,
            int port,
            String nonce,
            Duration timeout,
            String audience
    ) throws IOException {
        IOException firstFailure = null;
        for (String scheme : new String[]{"https", "http"}) {
            URI uri = URI.create(scheme + "://" + EndpointMatcher.formatEndpoint(host, port)
                    + MiguelNetworkProtocol.DISCOVERY_PATH);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(DiscoveryCodec.encodeRequest(nonce)))
                    .build();
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while discovering " + audience, exception);
            } catch (IOException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                }
            }
        }
        throw new IOException("Discovery unavailable over HTTPS and HTTP for " + audience, firstFailure);
    }
}
