# MiguelNetwork

Minecraft Mod for carrying Minecraft Java Edition TCP traffic over WSS by managing a bundled
[wstunnel](https://github.com/erebe/wstunnel) sidecar.

Current target: Minecraft 1.21.1 + NeoForge 21.1.77 + Java 21.

This is an Alpha-stage technical preview, not a production release. Client and server tunnel startup are disabled by
default. Client routing is opt-in per server through an allowlist. See `docs/CONFIGURATION.md`.

The completed Phase 0 evidence and remaining platform gaps are recorded in `docs/VALIDATION_REPORT.md`. Development
and system-property overrides are described in `docs/TECHNICAL_VALIDATION.md`.
