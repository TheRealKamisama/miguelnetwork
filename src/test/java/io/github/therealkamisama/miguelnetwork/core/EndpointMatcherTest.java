package io.github.therealkamisama.miguelnetwork.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointMatcherTest {
    @Test
    void emptyAllowlistRoutesNothing() {
        assertFalse(EndpointMatcher.matches("mc.example.com", 25565, List.of()));
    }

    @Test
    void supportsHostEndpointWildcardAndIpv6Entries() {
        assertTrue(EndpointMatcher.matches("MC.EXAMPLE.COM", 25565, List.of("mc.example.com")));
        assertTrue(EndpointMatcher.matches("mc.example.com", 443, List.of("mc.example.com:443")));
        assertTrue(EndpointMatcher.matches("play.example.com", 25565, List.of("*.example.com")));
        assertFalse(EndpointMatcher.matches("example.com", 25565, List.of("*.example.com")));
        assertTrue(EndpointMatcher.matches("2001:db8::1", 443, List.of("[2001:db8::1]:443")));
        assertTrue(EndpointMatcher.matches("anything.invalid", 1, List.of("*")));
    }
}
