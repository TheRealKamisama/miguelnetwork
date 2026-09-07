# Changelog

## 0.1.0-alpha.6

- Add `STANDALONE` and `EXTERNAL_PROXY` deployment modes.
- Add a built-in plain-WS gateway that multiplexes Discovery and wstunnel on one public port.
- Generate the dedicated-server config in `config/` and derive Minecraft target IP/port from `server.properties`.
- Auto-detect supported ZstdNet and publish the resolved runtime endpoints in `generated/detected-server.toml`.
- Make Discovery signing, WSS advertisement and persistent WSS downgrade enforcement optional and disabled by default.
- Add `wstunnelTargetHost` to Discovery routes so explicitly bound Minecraft servers work without manual client config.

## 0.1.0-alpha.5

- Compose ZstdNet's primary server-list UI path, which enters its connection hook with an existing loopback proxy and
  the bypass flag set.
- Recover the logical server address from `ServerData`, replace the premature direct proxy and clear the consumed
  ZstdNet bypass state.

## 0.1.0-alpha.4

- Fix ZstdNet composition on NeoForge by injecting directly into its optional connection hook.
- Stop depending on the undefined transformation order between ZstdNet's coremod and MiguelNetwork's mixins.

## 0.1.0-alpha.3

- Add signed MiguelNetwork Discovery Protocol v1 with dynamic per-server transport, public endpoint, path and target port.
- Persist Ed25519 server identity, client key pins, monotonic configuration epochs and a WSS downgrade floor.
- Add a loopback Discovery HTTP service designed for TLS/path multiplexing through Nginx.
- Compose ZstdNet 1.4.7 compressed streams with MiguelNetwork without modifying or reimplementing ZstdNet.
- Advertise separate ZstdNet and raw Minecraft routes and restrict wstunnel to both detected loopback targets.

## 0.1.0-alpha.2

- Remove client-side server allowlists and explicit WSS/WS selection from normal TOML configuration.
- Discover each endpoint as verified WSS, then WS, and fall back to unchanged vanilla TCP when neither probe succeeds.
- Use a protocol-fixed internal Minecraft target port and infer it from `server-port` on the server, so clients no
  longer configure or know the server's loopback port.
- Cache positive discoveries for the game process and retry negative TCP discoveries after 30 seconds.
- Add a protocol-compatible wstunnel WebSocket Upgrade probe and validate it against the live trusted-WSS endpoint.

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
