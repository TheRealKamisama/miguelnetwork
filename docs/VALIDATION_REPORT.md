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
- The resulting technical-preview JAR is 9,949,673 bytes with SHA-256
  `d20a0bf823c67caa4a165f56c6d6b3007223f3fb2fd1460d4ce3adaa05f4cc1b`.

## Defect found during validation

The host environment defines the conventional variable `NO_COLOR=1`. wstunnel v10.7.1's CLI parser attempts to parse
that environment value as a boolean and rejects `1`, even when `--no-color` is passed explicitly. MiguelNetwork now
removes inherited `NO_COLOR` from the child environment and supplies `--no-color` itself.

## Not yet verified

- Certificate success/failure behavior against a publicly trusted certificate.
- Linux execution on a Linux host.
- A separate UDP denial case; the current strict rule only declares `Tcp` and the wrong TCP destination was verified.
- Compatibility with SRV redirects and connection-altering Mods.
- Cleanup after JVM crash or forced termination.

## Current interpretation

The sidecar transport, binary packaging, NeoForge build and chosen central connection hook are technically viable. A
real Minecraft status Ping, login and playable session have crossed WSS twice. Phase 0 has therefore validated the core
architecture; public-certificate and cross-platform validation remain before a production-ready release.
