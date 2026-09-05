# MiguelNetwork

Technical validation project for carrying Minecraft Java Edition TCP traffic over WSS by managing a bundled
[wstunnel](https://github.com/erebe/wstunnel) sidecar.

Current target: Minecraft 1.21.1 + NeoForge 21.1.77 + Java 21.

This is a Phase 0 technical preview, not a production release. Client and server tunnel startup are disabled unless
their corresponding system properties are enabled. See `docs/TECHNICAL_VALIDATION.md`.

The current evidence and remaining gaps are recorded in `docs/VALIDATION_REPORT.md`.
