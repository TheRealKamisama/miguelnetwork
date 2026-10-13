package io.github.therealkamisama.miguelnetwork.interop;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StreamOrderTest {
    private Map<String,Object> peer(String id, Function<Map<String,Object>,?> callback) {
        return Map.of("streamLayer", StreamOrder.layer(id), "agreeStreamOrder", callback);
    }

    @Test void bothRolesDeriveTheSameOrderRegardlessOfNegotiationInitiator() {
        var compression = StreamOrder.agree(StreamOrder.ZSTD,
                Map.of("peerLayer", StreamOrder.layer(StreamOrder.TRANSPORT)));
        var transport = StreamOrder.agree(StreamOrder.TRANSPORT,
                Map.of("peerLayer", StreamOrder.layer(StreamOrder.ZSTD)));
        assertEquals(compression, transport);
        assertEquals(List.of(StreamOrder.ZSTD, StreamOrder.TRANSPORT), compression.get("outbound"));
        assertEquals(List.of(StreamOrder.TRANSPORT, StreamOrder.ZSTD), compression.get("inbound"));
        assertNotNull(StreamOrder.negotiatePeer(StreamOrder.ZSTD,
                peer(StreamOrder.TRANSPORT, request -> StreamOrder.agree(StreamOrder.TRANSPORT, request))));
        assertNotNull(StreamOrder.negotiatePeer(StreamOrder.TRANSPORT,
                peer(StreamOrder.ZSTD, request -> StreamOrder.agree(StreamOrder.ZSTD, request))));
    }

    @Test void reverseOrderAndConflictingPeerConstraintsCannotBeAgreed() {
        var reversed = StreamOrder.agree(StreamOrder.ZSTD, Map.of(
                "peerLayer", StreamOrder.layer(StreamOrder.TRANSPORT),
                "outbound", List.of(StreamOrder.TRANSPORT, StreamOrder.ZSTD)));
        assertFalse(StreamOrder.accepted(reversed));
        assertFalse(StreamOrder.accepted(StreamOrder.agree(StreamOrder.ZSTD, Map.of("peerLayer",
                Map.of("id", StreamOrder.TRANSPORT, "role", "encapsulation",
                        "before", List.of(StreamOrder.ZSTD), "after", List.of())))));
        assertThrows(IllegalArgumentException.class, () -> StreamOrder.require(reversed));
    }

    @Test void agreementRequiresThePeerAcknowledgementAndBothDirections() {
        assertNull(StreamOrder.negotiatePeer(StreamOrder.ZSTD, Map.of("streamLayer", StreamOrder.layer(StreamOrder.TRANSPORT))));
        assertNull(StreamOrder.negotiatePeer(StreamOrder.ZSTD,
                peer(StreamOrder.TRANSPORT, ignored -> Map.of("agreed", false))));
        assertNull(StreamOrder.negotiatePeer(StreamOrder.ZSTD,
                peer(StreamOrder.TRANSPORT, ignored -> Map.of("agreed", true,
                        "outbound", List.of(StreamOrder.ZSTD, StreamOrder.TRANSPORT),
                        "inbound", List.of(StreamOrder.ZSTD, StreamOrder.TRANSPORT)))));
        assertNull(StreamOrder.negotiatePeer(StreamOrder.ZSTD, peer(StreamOrder.TRANSPORT, ignored -> "invalid")));
    }
}
