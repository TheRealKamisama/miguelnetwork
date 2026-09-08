# MiguelNetwork 0.2.0

Minecraft Mod for carrying Minecraft Java Edition TCP traffic over WebSocket by managing a bundled
[wstunnel](https://github.com/erebe/wstunnel) sidecar.

The client requests a Discovery v1 manifest from the logical server address. A manifest can select a distinct WS/WSS
endpoint, path, internal target host/port and stream filter for every saved Minecraft server. Discovery signatures and
WSS are optional, and the legacy WSS/WS/TCP probe remains as a migration fallback.

Current target: Minecraft 1.21.1 + Java 21; MiguelNetwork is compiled against NeoForge 21.1.77. ZstdNet 1.4.7 and 1.4.8
have an optional, version-gated compatibility adapter which transports ZstdNet's compressed byte stream without copying
its compression implementation. Both verified ZstdNet JARs require NeoForge 21.1.221 or newer.

Version 0.2.0 is a technical preview and should be validated against the target modpack before production use.
Standalone mode is enabled by default and provides plain WS plus Discovery on one public port without Nginx.
External-proxy mode exposes separate private upstreams for an Nginx deployment that may use WSS. Minecraft and
supported ZstdNet target endpoints are detected automatically. The client needs no per-server allowlist or transport
selection. See `docs/USER_GUIDE.md` for the short installation and log checklist, then
`docs/CONFIGURATION.md`, `docs/DISCOVERY_PROTOCOL.md`, and `docs/ZSTDNET_COMPATIBILITY.md` for detailed deployment and
protocol behavior.

The completed Phase 0 evidence and remaining platform gaps are recorded in `docs/VALIDATION_REPORT.md`. Development,
testing, and system-property overrides are described in `docs/DEVELOPMENT.md` and `docs/TECHNICAL_VALIDATION.md`.

## Quick start

1. Install Java 21, NeoForge, and MiguelNetwork 0.2.0 on the server and client.
   ZstdNet 1.4.7/1.4.8 is optional; if installed, use NeoForge 21.1.221 or newer.
2. Leave the server in `STANDALONE`, choose an unused `publicPort`, and expose
   only that port. Keep Minecraft and ZstdNet listener ports private.
3. Start the server, confirm the standalone gateway and Discovery messages in
   `logs/latest.log`, and add `public-host:publicPort` to the client server list.
4. Confirm the client reports `selected Discovery route`. If the deployment must
   fail instead of probing legacy transports, set `legacyFallback = false` in
   `config/miguelnetwork-client.toml`.

For release preparation, artifact verification, and GitHub CLI guidance, see
`docs/RELEASE_CHECKLIST.md`; contributor and Agent instructions live in `AGENTS.md`
and `docs/DEVELOPMENT.md`.

MiguelNetwork is licensed under Apache-2.0. The bundled wstunnel executable remains under BSD-3-Clause; see
`THIRD_PARTY_NOTICES.md`.
