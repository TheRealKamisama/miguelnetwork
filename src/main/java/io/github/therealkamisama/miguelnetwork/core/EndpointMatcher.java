package io.github.therealkamisama.miguelnetwork.core;

import java.util.List;
import java.util.Locale;

public final class EndpointMatcher {
    private EndpointMatcher() {
    }

    public static boolean matches(String host, int port, List<String> entries) {
        String normalizedHost = normalizeHost(host);
        String endpoint = formatEndpoint(normalizedHost, port);
        for (String rawEntry : entries) {
            String entry = rawEntry.trim().toLowerCase(Locale.ROOT);
            if (entry.equals("*") || entry.equals(normalizedHost) || entry.equals(endpoint)) {
                return true;
            }
            if (entry.startsWith("*.") && matchesWildcard(normalizedHost, entry.substring(2))) {
                return true;
            }
        }
        return false;
    }

    public static String formatEndpoint(String host, int port) {
        String normalizedHost = normalizeHost(host);
        return normalizedHost.indexOf(':') >= 0
                ? "[" + normalizedHost + "]:" + port
                : normalizedHost + ":" + port;
    }

    private static String normalizeHost(String host) {
        String value = host.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("[") && value.endsWith("]")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean matchesWildcard(String host, String suffix) {
        return !suffix.isEmpty() && host.endsWith("." + suffix) && host.length() > suffix.length() + 1;
    }
}
