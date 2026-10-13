package io.github.therealkamisama.miguelnetwork.interop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Ordering concerns byte-stream processing only; it contains no transport implementation. */
public final class StreamOrder {
    public static final String ZSTD = "zstdnet-stream";
    public static final String TRANSPORT = "miguelnetwork-transport";
    private StreamOrder() {}

    public static Map<String,Object> layer(String id) {
        if (ZSTD.equals(id)) return Map.of("id", id, "role", "compression",
                "before", List.of(TRANSPORT), "after", List.of());
        if (TRANSPORT.equals(id)) return Map.of("id", id, "role", "encapsulation",
                "before", List.of(), "after", List.of(ZSTD));
        throw new IllegalArgumentException("Unknown stream layer");
    }

    public static Map<String,Object> agree(String self, Map<String,Object> request) {
        Object offered = request.get("peerLayer");
        String other = ZSTD.equals(self) ? TRANSPORT : ZSTD;
        if (!(offered instanceof Map<?,?> peer) || !layer(other).equals(peer)) {
            return Map.of("agreed", false, "reason", "Incompatible peer stream-layer constraints");
        }
        Map<String,Object> own = layer(self);
        List<String> candidate = new ArrayList<>(List.of(self, other));
        if (!satisfies(candidate, own) || !satisfies(candidate, peer)) Collections.reverse(candidate);
        if (!satisfies(candidate, own) || !satisfies(candidate, peer)) {
            return Map.of("agreed", false, "reason", "No compatible stream ordering");
        }
        List<String> outbound = List.copyOf(candidate);
        if (request.containsKey("outbound") && !outbound.equals(request.get("outbound"))) {
            return Map.of("agreed", false, "reason", "Requested stream order conflicts with layer constraints");
        }
        Collections.reverse(candidate);
        return Map.of("agreed", true, "outbound", outbound, "inbound", List.copyOf(candidate));
    }

    private static boolean satisfies(List<String> order, Map<?,?> constraint) {
        int position = order.indexOf(constraint.get("id"));
        if (position < 0 || !(constraint.get("before") instanceof List<?> before)
                || !(constraint.get("after") instanceof List<?> after)) return false;
        return before.stream().allMatch(id -> order.indexOf(id) > position)
                && after.stream().allMatch(id -> order.indexOf(id) >= 0 && order.indexOf(id) < position);
    }

    public static boolean accepted(Object response) {
        return response instanceof Map<?,?> result && Boolean.TRUE.equals(result.get("agreed"))
                && List.of(ZSTD, TRANSPORT).equals(result.get("outbound"))
                && List.of(TRANSPORT, ZSTD).equals(result.get("inbound"));
    }

    /** Both peers must acknowledge the same ordering before either opens a native stream. */
    @SuppressWarnings("unchecked")
    public static Map<String,Object> negotiatePeer(String self, Map<?,?> descriptor) {
        Object offered = descriptor.get("streamLayer");
        Object callback = descriptor.get("agreeStreamOrder");
        if (!(offered instanceof Map<?,?>) || !(callback instanceof Function<?,?> function)) return null;
        Map<String,Object> local = agree(self, Map.of("peerLayer", offered));
        if (!accepted(local)) return null;
        Object remote = ((Function<Map<String,Object>,?>) function).apply(
                Map.of("peerLayer", layer(self), "outbound", local.get("outbound")));
        return accepted(remote) ? local : null;
    }

    /** Normalize a received agreement; do not retain a caller's mutable lists/maps. */
    public static Map<String,Object> require(Object value) {
        if (!accepted(value)) throw new IllegalArgumentException("Native stream order was not agreed");
        return Map.of("agreed", true, "outbound", List.of(ZSTD, TRANSPORT),
                "inbound", List.of(TRANSPORT, ZSTD));
    }
}
