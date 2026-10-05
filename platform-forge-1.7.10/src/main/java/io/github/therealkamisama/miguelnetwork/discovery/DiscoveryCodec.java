package io.github.therealkamisama.miguelnetwork.discovery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.therealkamisama.miguelnetwork.core.MiguelNetworkProtocol;
import io.github.therealkamisama.miguelnetwork.core.TransportProtocol;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

public final class DiscoveryCodec {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();
    public static final int MAX_DOCUMENT_BYTES = 64 * 1024;

    private DiscoveryCodec() {
    }

    public static byte[] encodeAndSign(DiscoveryManifest manifest, PrivateKey privateKey, PublicKey publicKey)
            throws GeneralSecurityException {
        byte[] payload = encodePayload(manifest);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(payload);

        JsonObject envelope = new JsonObject();
        envelope.addProperty("payload", BASE64.encodeToString(payload));
        envelope.addProperty("signature", BASE64.encodeToString(signer.sign()));
        envelope.addProperty("publicKey", BASE64.encodeToString(publicKey.getEncoded()));
        return GSON.toJson(envelope).getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] encodeUnsigned(DiscoveryManifest manifest) {
        return encodePayload(manifest);
    }

    public static DiscoveryManifest decodeWithoutSignatureVerification(byte[] document)
            throws GeneralSecurityException {
        if (document.length == 0 || document.length > MAX_DOCUMENT_BYTES) {
            throw new GeneralSecurityException("Discovery document is empty or oversized");
        }
        try {
            JsonObject root = GSON.fromJson(new String(document, StandardCharsets.UTF_8), JsonObject.class);
            return root != null && root.has("payload")
                    ? decodePayload(decodeRequired(root, "payload"))
                    : decodePayload(document);
        } catch (RuntimeException exception) {
            throw new GeneralSecurityException("Malformed Discovery document", exception);
        }
    }

    public static SignedDiscoveryDocument decodeAndVerify(byte[] document)
            throws GeneralSecurityException {
        if (document.length == 0 || document.length > MAX_DOCUMENT_BYTES) {
            throw new GeneralSecurityException("Discovery document is empty or oversized");
        }
        try {
            JsonObject envelope = GSON.fromJson(new String(document, StandardCharsets.UTF_8), JsonObject.class);
            byte[] payload = decodeRequired(envelope, "payload");
            byte[] signature = decodeRequired(envelope, "signature");
            byte[] encodedPublicKey = decodeRequired(envelope, "publicKey");
            PublicKey publicKey = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(encodedPublicKey));

            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(payload);
            if (!verifier.verify(signature)) {
                throw new GeneralSecurityException("Invalid Discovery signature");
            }

            DiscoveryManifest manifest = decodePayload(payload);
            if (!manifest.keyId().equals(keyId(publicKey))) {
                throw new GeneralSecurityException("Discovery key id does not match its public key");
            }
            return new SignedDiscoveryDocument(manifest, payload, signature, publicKey);
        } catch (RuntimeException exception) {
            throw new GeneralSecurityException("Malformed Discovery document", exception);
        }
    }

    public static byte[] encodePayload(DiscoveryManifest manifest) {
        JsonObject root = new JsonObject();
        root.addProperty("protocol", manifest.protocol());
        root.addProperty("serverId", manifest.serverId());
        root.addProperty("audience", manifest.audience());
        root.addProperty("clientNonce", manifest.clientNonce());
        root.addProperty("configEpoch", manifest.configEpoch());
        root.addProperty("issuedAt", manifest.issuedAt());
        root.addProperty("expiresAt", manifest.expiresAt());
        root.add("routes", GSON.toJsonTree(manifest.routes()));
        root.addProperty("keyId", manifest.keyId());
        return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
    }

    public static DiscoveryManifest decodePayload(byte[] payload) {
        JsonObject root = GSON.fromJson(new String(payload, StandardCharsets.UTF_8), JsonObject.class);
        List<DiscoveryRoute> routes = new ArrayList<>();
        root.getAsJsonArray("routes").forEach(element -> {
            JsonObject route = element.getAsJsonObject();
            List<DiscoveryFilter> filters = new ArrayList<>();
            if (route.has("filters")) {
                route.getAsJsonArray("filters").forEach(filterElement -> {
                    JsonObject filter = filterElement.getAsJsonObject();
                    filters.add(new DiscoveryFilter(
                            requiredString(filter, "id"),
                            filter.get("version").getAsInt(),
                            filter.get("required").getAsBoolean()
                    ));
                });
            }
            routes.add(new DiscoveryRoute(
                    requiredString(route, "id"),
                    TransportProtocol.valueOf(requiredString(route, "transport").toUpperCase()),
                    requiredString(route, "host"),
                    route.get("port").getAsInt(),
                    requiredString(route, "pathPrefix"),
                    route.has("wstunnelTargetHost")
                            ? requiredString(route, "wstunnelTargetHost") : "127.0.0.1",
                    route.get("wstunnelTargetPort").getAsInt(),
                    route.get("priority").getAsInt(),
                    filters
            ));
        });
        routes.sort(Comparator.comparingInt(DiscoveryRoute::priority).reversed());
        return new DiscoveryManifest(
                requiredString(root, "protocol"),
                requiredString(root, "serverId"),
                requiredString(root, "audience"),
                requiredString(root, "clientNonce"),
                root.get("configEpoch").getAsLong(),
                root.get("issuedAt").getAsLong(),
                root.get("expiresAt").getAsLong(),
                routes,
                requiredString(root, "keyId")
        );
    }

    public static byte[] encodeRequest(String nonce) {
        JsonObject request = new JsonObject();
        request.addProperty("protocol", MiguelNetworkProtocol.DISCOVERY_PROTOCOL);
        request.addProperty("nonce", nonce);
        return GSON.toJson(request).getBytes(StandardCharsets.UTF_8);
    }

    public static String decodeRequestNonce(byte[] request) {
        if (request.length == 0 || request.length > 4096) {
            throw new IllegalArgumentException("Discovery request is empty or oversized");
        }
        JsonObject root = GSON.fromJson(new String(request, StandardCharsets.UTF_8), JsonObject.class);
        if (!MiguelNetworkProtocol.DISCOVERY_PROTOCOL.equals(requiredString(root, "protocol"))) {
            throw new IllegalArgumentException("Unsupported Discovery protocol");
        }
        String nonce = requiredString(root, "nonce");
        byte[] decoded = BASE64_DECODER.decode(nonce);
        if (decoded.length != 32) {
            throw new IllegalArgumentException("Discovery nonce must contain 256 bits");
        }
        return nonce;
    }

    public static String keyId(PublicKey publicKey) throws GeneralSecurityException {
        return BASE64.encodeToString(MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded()));
    }

    public static String encodePublicKey(PublicKey publicKey) {
        return BASE64.encodeToString(publicKey.getEncoded());
    }

    private static byte[] decodeRequired(JsonObject object, String name) {
        return BASE64_DECODER.decode(requiredString(object, name));
    }

    private static String requiredString(JsonObject object, String name) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            throw new IllegalArgumentException("Missing field: " + name);
        }
        String value = object.get(name).getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Blank field: " + name);
        }
        return value;
    }
}
