package io.github.therealkamisama.miguelnetwork.core;

import java.util.Locale;

public enum TransportProtocol {
    WSS("wss", true),
    WS("ws", false);

    private final String scheme;
    private final boolean tls;

    TransportProtocol(String scheme, boolean tls) {
        this.scheme = scheme;
        this.tls = tls;
    }

    public String scheme() {
        return scheme;
    }

    public boolean usesTls() {
        return tls;
    }

    public static TransportProtocol property(String name, TransportProtocol fallback) {
        String value = System.getProperty(name);
        return value == null ? fallback : valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
