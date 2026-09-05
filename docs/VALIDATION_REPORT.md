# Phase 0 validation report

Date: 2026-09-05

## Verified

- Official NeoForge MDK history was pinned to Minecraft 1.21.1 and NeoForge 21.1.77.
- Java 21 compilation completed against generated Minecraft/NeoForge sources.
- The client development runtime launched successfully and loaded MiguelNetwork with Mixin 0.8.7; no Mixin target or
  injection error was reported during startup.
- The actual 1.21.1 source confirms both status Ping and normal login ultimately call
  `Connection.connect(InetSocketAddress, boolean, Connection)`, while their callers retain the original resolved address
  for the Minecraft intention packet. The Phase 0 central argument redirect therefore changes the TCP socket target
  without rewriting the Minecraft handshake address.
- Official wstunnel v10.7.1 Windows x64 and Linux x64 archives matched the SHA-256 values published on the release page.
- Hashes of the extracted binaries are embedded in code and in `META-INF/miguelnetwork/wstunnel-manifest.json`.
- The embedded Windows binary reported `wstunnel-cli 10.7.1` when executed.
- A local black-box path was exercised successfully:

  ```text
  HTTP client -> 127.0.0.1:19091 -> wstunnel client
              -> ws://127.0.0.1:18080 -> wstunnel server
              -> 127.0.0.1:19090 -> local HTTP server
  ```

  The request returned HTTP 200 with the expected directory listing. Client, server and target test processes were then
  stopped.
- The strict MiguelNetwork restriction YAML was exercised against wstunnel itself. TCP to the configured loopback target
  returned HTTP 200, while a request using the same accepted path prefix but a different loopback destination port was
  rejected by wstunnel as `not allowed destination`.
- Gradle `build` completed with unit tests after the binaries were embedded.
- A complete NeoForge 1.21.1 client-to-dedicated-server flow was exercised twice over local WSS:

  ```text
  NeoForge client -> local wstunnel TCP listener -> WSS 127.0.0.1:25565
                  -> server wstunnel -> Minecraft TCP 127.0.0.1:25566
  ```

  The multiplayer screen's short-lived status Ping tunnels completed before each login. The two gameplay tunnels were
  accepted by the `MiguelNetwork Minecraft only` restriction and connected to `127.0.0.1:25566`. The dedicated server
  recorded `Dev joined the game` at 01:44:06 and 01:45:45, and both sessions were playable before normal disconnects.
  A simultaneous port-owner check showed `wstunnel.exe` listening on 25565 and the Minecraft Java process listening on
  25566, excluding an accidental direct connection to the Minecraft listener.
- The local WSS test used wstunnel's built-in self-signed certificate and the new explicit development-only client
  bypass. Secure defaults remain unchanged: client certificate verification is enabled by default, and server startup
  requires configured certificate files unless built-in self-signed mode is explicitly allowed.
- The WS-capable technical-preview JAR is 9,966,544 bytes with SHA-256
  `8d9ae26a230454f3e1a434fe92735ec4e499442783a8702440905932891f1dc6`.

## Defect found during validation

The host environment defines the conventional variable `NO_COLOR=1`. wstunnel v10.7.1's CLI parser attempts to parse
that environment value as a boolean and rejects `1`, even when `--no-color` is passed explicitly. MiguelNetwork now
removes inherited `NO_COLOR` from the child environment and supplies `--no-color` itself.

## Phase 1 configuration validation

- Native NeoForge TOML configuration was loaded on both sides without JVM property overrides.
- With `127.0.0.1:25565` allowlisted, Minecraft quick-play started a client sidecar, completed WSS/TLS, matched the
  server restriction, connected to the inferred Minecraft port 25566, and joined the world.
- With the same address removed from the allowlist, no client sidecar was created. Minecraft's unchanged TCP connection
  reached the WSS listener directly and was rejected as an invalid TLS content type, confirming that unmatched entries
  follow the vanilla connection path.
- Client shutdown removed its sidecar. Force-terminating the Windows Gradle server batch process bypassed the normal
  Minecraft stop event and left its sidecar running; it was detected and removed manually. Hard parent termination
  therefore remains an explicit limitation.
- Review identified that a single global client sidecar would make concurrent status Pings for different WSS servers
  interrupt each other. Phase 1 now retains one sidecar per endpoint with a configurable, LRU-bounded process limit.

## Cross-host Linux WS validation

- An isolated production-style server was installed in an Ubuntu 22.04 x86-64 LXC container using Minecraft 1.21.1,
  NeoForge 21.1.77 and Zulu Java 21.0.12.1. Its mod list contained only Minecraft, NeoForge and MiguelNetwork.
- The dedicated server bound Minecraft to `127.0.0.1:25566`. MiguelNetwork extracted and launched the embedded Linux
  wstunnel v10.7.1 binary as an unencrypted WS listener on `0.0.0.0:25565`.
- A wstunnel client on a separate Windows host connected across the LAN to `ws://192.168.0.146:25565`. A real Minecraft
  status Ping crossed that tunnel and returned Minecraft `1.21.1`, protocol `767`, the configured MOTD and an empty
  player list.
- Server logs independently recorded the remote peer, matched the `MiguelNetwork Minecraft only` restriction, and
  opened only the permitted TCP destination `127.0.0.1:25566`.
- This proves the cross-platform Windows-client/Linux-server WS transport and Linux bundled-binary extraction. Public
  NAT traversal, login and gameplay remain to be exercised before treating the public WS experiment as complete.

## Not yet verified

- Certificate success/failure behavior against a publicly trusted certificate.
- A separate UDP denial case; the current strict rule only declares `Tcp` and the wrong TCP destination was verified.
- Compatibility with SRV redirects and connection-altering Mods.
- Cleanup after JVM crash or forced termination.
- Public-internet status Ping, login, gameplay and reconnect behavior.

## Current interpretation

The sidecar transport, binary packaging, NeoForge build and chosen central connection hook are technically viable. A
real Minecraft status Ping, login and playable session have crossed WSS twice, and a cross-host status Ping has crossed
plain WS between Windows and Linux. Phase 0 has therefore validated the core architecture; public-internet gameplay,
trusted-certificate and broader compatibility validation remain before a production-ready release.
