# Forge 1.7.10 / Java 25 platform

This port is an independent build in `platform-forge-1.7.10`. It is a transport
adapter, not a protocol translator: both ends still need Minecraft 1.7.10 and a
compatible mod set. See [the validation report](FORGE_1_7_10_JAVA_25_VALIDATION.md)
for the tested configuration and validation scope.

## Runtime components

| Component | Pinned baseline |
|---|---|
| Minecraft | 1.7.10, exact accepted range `[1.7.10]` |
| Forge | 10.13.4.1614 |
| Java | Azul Zulu 25.0.4.1+1, 64 bit |
| LWJGL3ify | 3.0.33 mod plus matching forgePatches launcher |
| RetroFuturaBootstrap | 1.1.1, included in that forgePatches JAR |
| UniMixins | 0.3.1 combined JAR, including GTNHMixins |
| LWJGL client libraries | 3.4.2, supplied by the matching launcher patch |
| Native transport | wstunnel 10.7.1, Windows x86-64 or Linux x86-64 |

Windows x86-64 runtime behavior was validated. The Linux x86-64 payload and hashes
are verified, but Linux runtime testing was explicitly excluded and remains untested.

Hodgepodge is recommended by LWJGL3ify for compatibility with other mods. It is
not required by this minimal MiguelNetwork baseline. Test any additional modpack
components separately. Hybrid servers such as Thermos/Crucible are outside this
setup. ZstdNet integration is absent: no adapter or ZstdNet Mixin is packaged,
and required unsupported Discovery filters are rejected.

## Client installation

1. Create a separate Minecraft 1.7.10 instance in Prism Launcher or MultiMC.
2. Apply the official
   [LWJGL3ify 3.0.33 MultiMC patch](https://github.com/GTNewHorizons/lwjgl3ify/releases/download/3.0.33/lwjgl3ify-3.0.33-multimc.zip)
   to that instance, including its `mmc-pack.json` and all patch JSON files.
   The `launchargs` patch selects `MainStartOnFirstThread`; omitting that patch
   prevents LWJGL3ify 3.x client startup.
3. Install the matching
   [LWJGL3ify mod](https://github.com/GTNewHorizons/lwjgl3ify/releases/download/3.0.33/lwjgl3ify-3.0.33.jar),
   [UniMixins combined JAR](https://nexus.gtnewhorizons.com/repository/public/io/github/legacymoddingmc/unimixins/0.3.1/unimixins-0.3.1.jar),
   and `miguelnetwork-forge-1.7.10-0.2.1.jar` in the instance's `mods/` directory.
4. Select the Java 25 runtime. Prism reads the supplied JVM arguments from the
   patches. MultiMC users must also apply the matching
   [Java arguments](https://github.com/GTNewHorizons/lwjgl3ify/blob/3.0.33/java9args.txt).
5. Add the server's public WS/Discovery host and port, for example
   `play.example.test:35548`. A standalone server needs no TLS certificate.

## Dedicated server installation

Install a fresh Forge 10.13.4.1614 server for Minecraft 1.7.10. Keep its universal
JAR, `minecraft_server.1.7.10.jar`, and installed `libraries/` directory. Install
the same three mods listed above. Download the matching
[forgePatches JAR](https://github.com/GTNewHorizons/lwjgl3ify/releases/download/3.0.33/lwjgl3ify-3.0.33-forgePatches.jar)
beside the server JARs, rename it `lwjgl3ify-forgePatches.jar`, and save the pinned
`java9args.txt` there. After accepting the Minecraft EULA yourself, launch with:

```text
java -Xms1G -Xmx2G @java9args.txt -jar lwjgl3ify-forgePatches.jar nogui
```

`java` must refer to Java 25. Allocate memory appropriate to the modpack. The
Minecraft listener keeps its real `server.properties` bind/port. MiguelNetwork
starts after Forge reports the server ready and forwards to that actual listener.
Its public port must differ from the Minecraft port.

## Default deployment

`STANDALONE` is primary and enabled by default. One public port (35548) serves
unsigned Discovery v1 and plain WebSocket. Open that port for players. No proxy,
certificate, signature, or mandatory downgrade policy is required.

On startup expect:

```text
MiguelNetwork standalone WS gateway is ready on <bind>:<port>; Discovery and wstunnel share one port
MiguelNetwork selected Discovery route minecraft-primary for <host>:<port>
```

`Discovery unavailable`, `selected legacy ... route`, and `discovered vanilla TCP
endpoint` indicate fallback entry/use. Set `legacyFallback=false` on clients when
Discovery failure must reject a connection. Invalid manifests and unsupported
required filters always reject; they never trigger legacy probing.

Only the Netty socket destination is redirected. Minecraft's handshake host,
port, and FML marker are preserved. Minecraft's DNS/SRV resolution remains in its
normal caller path. A redirected connection waits for Netty to finish initializing Minecraft before
returning. Closed routed connections report a disconnect reason. Minecraft's old
raw-TCP status retry is suppressed for a query that already selected Discovery.
Each socket leases its tunnel, so a status ping closing does
not close an active gameplay tunnel. The process limit rejects additional routes
without evicting active connections. Disconnect/failure releases the lease; the
last lease closes its sidecar.

## Configuration

This platform uses UTF-8 Java `.properties` files instead of NeoForge TOML.
Nested sections become dotted keys. Values have no TOML quotes or section headers.
Missing files are created at first startup; existing files are read without being
rewritten. Restart after editing. Invalid boolean, enum, or out-of-range values
fail configuration loading.

`config/miguelnetwork-server.properties`:

| Key | Default |
|---|---|
| `enabled` | `true` |
| `mode` | `STANDALONE` |
| `bindHost` | `0.0.0.0` |
| `publicPort` | `35548` |
| `pathPrefix` | `miguelnetwork-v1` |
| `discovery.enabled` | `true` |
| `discovery.signResponses` | `false` |
| `discovery.bindHost` | `127.0.0.1` |
| `discovery.port` | `25568` |
| `discovery.advertisedTransport` | `WS` |
| `discovery.advertisedHost` | empty, derives from request Host |
| `discovery.advertisedPort` | `0`, derives from request Host |
| `discovery.configEpoch` | `1` |
| `discovery.validitySeconds` | `120` |

`config/miguelnetwork-client.properties`:

| Key | Default |
|---|---|
| `enabled` | `true` |
| `pathPrefix` | `miguelnetwork-v1` |
| `maxTunnelProcesses` | `8` (range 1–32) |
| `legacyFallback` | `true` |
| `security.verifyDiscoverySignatures` | `false` |
| `security.enforceWssDowngradeProtection` | `false` |
| `security.verifyTlsCertificates` | `true` |

`config/miguelnetwork-common.properties` has `wstunnelDownload.enable=false`.
The normal JAR bundles both native binaries. The no-native JAR needs the explicit
download opt-in or a verified operator-supplied executable; see
[the disclosure](../platform-forge-1.7.10/NO_NATIVE.md).

Optional existing JVM overrides remain available, including
`miguelnetwork.public.port`, `miguelnetwork.bindHost`,
`miguelnetwork.server.mode`, `miguelnetwork.client.legacyFallback`,
`miguelnetwork.wstunnel.path`, and `miguelnetwork.wstunnel.download.enabled`.
Their names are defined in the platform's config classes; file keys and JVM
override names are distinct.

## Optional external WSS and signing

For an operator-selected TLS deployment, set `mode=EXTERNAL_PROXY`, bind the
private upstream as appropriate, and let Nginx/Caddy/another gateway own the public
WSS endpoint. Route `/.well-known/miguelnetwork/v1` to the private Discovery HTTP
port and WebSocket upgrades to the private WS port. Advertise the public endpoint
with `discovery.advertisedTransport=WSS` and, where necessary, the public host and
port. The hop from the external gateway to the mod remains plain WS. Certificate
management belongs to the gateway.

To enable signed Discovery, set server `discovery.signResponses=true` and client
`security.verifyDiscoverySignatures=true`. Generated Ed25519 identity files live
under `config/miguelnetwork/generated/`; the client trust store is
`config/miguelnetwork/client-trust.json`. Preserve these runtime files and keep
them out of source control. Signing and WSS downgrade protection remain optional.

## Building

The official ExampleMod1.7.10 starter wrapper pins Gradle 9.7.1 and GTNHGradle
2.0.33. Use Azul JDK 25 for the Gradle daemon, compiler, tests, and modern run tasks.
RetroFuturaGradle also uses a Java 8 toolchain for the historical Minecraft source
and launcher; this does not change the mod's Java 25 requirement.

```powershell
cd platform-forge-1.7.10
.\gradlew.bat test
.\gradlew.bat build --no-daemon
.\gradlew.bat runClient25
.\gradlew.bat runServer25
```

Use `./gradlew` on POSIX. Set `JAVA_HOME` to the selected JDK. For the proxy used
during validation, set `JAVA_OPTS` to
`-Dhttp.proxyHost=localhost -Dhttp.proxyPort=7897 -Dhttps.proxyHost=localhost -Dhttps.proxyPort=7897`.
This is a build-download setting; it is not a gameplay proxy configuration.
Optional `-PwstunnelArchiveDir=<directory>` uses local upstream archives, still
checking both archive and binary SHA-256 against the manifest.

Production outputs are `build/libs/miguelnetwork-forge-1.7.10-0.2.1.jar` and
`build/libs/miguelnetwork-forge-1.7.10-0.2.1-no-native.jar`. `reobfJar` performs
production SRG remapping. `verifyDistribution`, `verifyNoNativeDistribution`, and
`verifyPlatform` check native payloads, metadata, relocation, bytecode, and refmap.

The implementation uses native Java 25 class files (major 69), including records,
virtual threads, HttpClient, and Ed25519. The small, separate connection/status/cancellation Mixin
bridges target Java 8 (major 52 / `JAVA_8` Mixin compatibility) because UniMixins
0.3.1 loses its shaded ASM implementation-version metadata and rejects `JAVA_25`.
No Jabel or JVM Downgrader is used. Both compilers are the Java 25 compiler;
only the bridge's `--release` differs.
