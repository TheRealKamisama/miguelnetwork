# Changelog

## 0.1.0-alpha.1

- License MiguelNetwork under Apache-2.0 while retaining wstunnel's BSD-3-Clause notices.
- Add native NeoForge client and dedicated-server TOML configuration.
- Add opt-in client routing by exact host, endpoint, wildcard subdomain, or global wildcard.
- Infer the server tunnel target from the actual Minecraft listener when `targetPort` is zero.
- Add normal game/JVM shutdown cleanup for bundled wstunnel processes.
- Retain explicit, warning-heavy self-signed TLS switches for isolated development only.
- Document installation-time configuration and the completed end-to-end WSS validation.

## 0.0.1-tech-preview

- Embed official wstunnel v10.7.1 binaries for Windows x64 and Linux x64 with SHA-256 verification.
- Redirect the central Minecraft client connection path through a managed local wstunnel listener.
- Add a managed dedicated-server WSS listener with a fixed loopback-only restriction.
- Validate status Ping and two playable NeoForge sessions over local WSS.
