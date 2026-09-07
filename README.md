# MiguelNetwork

Minecraft Mod for carrying Minecraft Java Edition TCP traffic over WebSocket by managing a bundled
[wstunnel](https://github.com/erebe/wstunnel) sidecar.

The client requests a Discovery v1 manifest from the logical server address. A manifest can select a distinct WS/WSS
endpoint, path, internal target host/port and stream filter for every saved Minecraft server. Discovery signatures and
WSS are optional, and the legacy WSS/WS/TCP probe remains as a migration fallback.

Current target: Minecraft 1.21.1 + NeoForge 21.1.77 + Java 21. ZstdNet 1.4.7 has an optional, version-gated compatibility
adapter which transports ZstdNet's compressed byte stream without copying its compression implementation.

This is an Alpha-stage technical preview, not a production release. Standalone mode is enabled by default and provides
plain WS plus Discovery on one public port without Nginx. External-proxy mode exposes separate private upstreams for an
Nginx deployment that may use WSS. Minecraft and supported ZstdNet target endpoints are detected automatically. The
client needs no per-server allowlist or transport selection. See `docs/CONFIGURATION.md`, `docs/DISCOVERY_PROTOCOL.md`, and
`docs/ZSTDNET_COMPATIBILITY.md`.

The completed Phase 0 evidence and remaining platform gaps are recorded in `docs/VALIDATION_REPORT.md`. Development
and system-property overrides are described in `docs/TECHNICAL_VALIDATION.md`.

MiguelNetwork is licensed under Apache-2.0. The bundled wstunnel executable remains under BSD-3-Clause; see
`THIRD_PARTY_NOTICES.md`.
