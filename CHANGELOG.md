# Changelog

## 0.1.0-alpha.1

- License MiguelNetwork under Apache-2.0 while retaining wstunnel's BSD-3-Clause notices.
- Record a preliminary license audit of wstunnel's locked Rust dependency closure.
- Add native NeoForge client and dedicated-server TOML configuration.
- Allow development runs to honor TOML values without forced `enabled=false` system-property overrides.
- Add opt-in client routing by exact host, endpoint, wildcard subdomain, or global wildcard.
- Keep independent, LRU-bounded sidecars for concurrent status Pings to multiple WSS endpoints.
- Separate client and server development game directories to avoid concurrent log-file contention.
- Infer the server tunnel target from the actual Minecraft listener when `targetPort` is zero.
- Add normal game/JVM shutdown cleanup for bundled wstunnel processes.
- Retain explicit, warning-heavy self-signed TLS switches for isolated development only.
- Add explicit plain WS transport for isolated tests and TLS-terminating reverse-proxy deployments; WSS remains the
  default.
- Document installation-time configuration and the completed end-to-end WSS validation.

## 0.0.1-tech-preview

- Embed official wstunnel v10.7.1 binaries for Windows x64 and Linux x64 with SHA-256 verification.
- Redirect the central Minecraft client connection path through a managed local wstunnel listener.
- Add a managed dedicated-server WSS listener with a fixed loopback-only restriction.
- Validate status Ping and two playable NeoForge sessions over local WSS.
