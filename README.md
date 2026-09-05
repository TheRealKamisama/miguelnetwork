# MiguelNetwork

Minecraft Mod for carrying Minecraft Java Edition TCP traffic over WebSocket by managing a bundled
[wstunnel](https://github.com/erebe/wstunnel) sidecar.

WSS is the secure default. Explicit plain WS support is available for isolated testing and for a server-side listener
placed behind a TLS-terminating reverse proxy.

Current target: Minecraft 1.21.1 + NeoForge 21.1.77 + Java 21.

This is an Alpha-stage technical preview, not a production release. Client and server tunnel startup are disabled by
default. Client routing is opt-in per server through an allowlist. See `docs/CONFIGURATION.md`.

The completed Phase 0 evidence and remaining platform gaps are recorded in `docs/VALIDATION_REPORT.md`. Development
and system-property overrides are described in `docs/TECHNICAL_VALIDATION.md`.

MiguelNetwork is licensed under Apache-2.0. The bundled wstunnel executable remains under BSD-3-Clause; see
`THIRD_PARTY_NOTICES.md`.
