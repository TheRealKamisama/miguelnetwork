# ZstdNet Fabric compatibility

This branch provides a version- and API-gated adapter for ZstdNet Fabric 1.4.7
and 1.4.8 on Minecraft 1.21.1. Published JAR metadata and bytecode were inspected
on 2026-09-17. See FABRIC_VALIDATION.md for the actual runtime verification scope;
API inspection alone does not establish successful gameplay.

| MC | ZstdNet | CurseForge file | SHA-256 |
|---|---|---|---|
| 1.21.1 | 1.4.7 | 8443113 | 893FC4853A87530CA5F40A8BC387ECE8A424B9D9C3B40E5C8A930B8E61A08667 |
| 1.21.1 | 1.4.8 | 8752154 | 7EEA2701D4F89CD1CA152950832E281C715C4430C91CB69BE191CB213F7061B9 |

Source pages: https://www.curseforge.com/minecraft/mc-mods/zstdnet/files/8443113
and https://www.curseforge.com/minecraft/mc-mods/zstdnet/files/8752154 .
The JARs require Fabric Loader >=0.16.10, Fabric API, and Java >=21.

For an advertised compressed route:
Minecraft -> ZstdNet client proxy -> wstunnel client -> WS/WSS ->
wstunnel server -> ZstdNet listener -> Minecraft backend.

The Fabric adapter intercepts ZstdNet's own ConnectScreenHooks, preserving its
UI bypass handling and ProxyHandle ownership. It does not patch the ZstdNet
JAR or reimplement compression. Original Minecraft handshake host/port remain
unchanged. Internal loopback connections are marked to prevent double tunnelling.
For an advertised raw route, the owned proxy uses ZstdNet's RAW mode.

Before applying the optional Mixin, bytecode inspection checks the exact hook
descriptor, bypass flag, handoff fields, proxy factory/modes, and handle methods
without initializing the target class. Once accepted, the injection is required
to match; it must not silently fail and advertise unsupported compression.
A missing or unsupported Mod disables the optional adapter.

The server discovers readListenPort() reflectively after the default Fabric
SERVER_STARTED phase. Required unknown filters or unavailable compression are
never accepted. An advertised raw route may be selected instead; no compatible
route fails closed. A proxy failure after selecting a route refuses that
connection instead of falling through to ZstdNet's original direct connection.