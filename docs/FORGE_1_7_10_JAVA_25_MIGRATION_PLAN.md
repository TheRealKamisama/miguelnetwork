# MiguelNetwork: Minecraft 1.7.10 / Java 25 migration goal

Prepared 2026-10-03. Status: implementation handoff; no 1.7.10 port has yet been built or run.

## Goal and scope

Implement a separate MiguelNetwork Forge 1.7.10 port targeting Java 25 using the
GTNewHorizons ExampleMod1.7.10 starter and its native modern-Java mode. Deliver
source, a production JAR, reproducible build instructions, installation instructions,
and an evidence-based validation report. Preserve the existing NeoForge 1.21.1 /
Java 21 implementation and its behavior. This is a transport port, not a Minecraft
protocol translator: clients still require a compatible 1.7.10 server and mod set.

The user requested implementation in a separate chat using GPT-6 Astra with high
reasoning. That chat should register this objective with the goal tool and work
toward it autonomously. Do not invent a token budget. Do not mark the goal complete
while required build or runtime validation remains outstanding.

Local implementation and verification are authorized by this handoff. Commit, tag,
push, GitHub login, repository/release creation, and artifact publication are not
authorized. Follow AGENTS.md and retain user-owned edits and runtime files.

## Evidence and uncertainties

The preceding assessment read local source and these upstream pages on 2026-10-03:

- [Template README](https://github.com/GTNewHorizons/ExampleMod1.7.10): recommends
  its starter ZIP rather than cloning the example repository as a new mod.
- [Starter ZIP](https://github.com/GTNewHorizons/ExampleMod1.7.10/releases/download/master-packages/starter.zip).
- [Template properties](https://github.com/GTNewHorizons/ExampleMod1.7.10/blob/master/gradle.properties):
  Minecraft 1.7.10, Forge 10.13.4.1614, stable mappings 12; default
  `enableModernJavaSyntax=jabel`; supported `modern` mode means native modern
  bytecode without downgrading. Jabel alone does not provide modern JDK APIs.
- [Template settings](https://github.com/GTNewHorizons/ExampleMod1.7.10/blob/master/settings.gradle.kts):
  observed convention plugin version 2.0.33. This is an observation, not a tested baseline.
- [GTNHGradle](https://github.com/GTNewHorizons/GTNHGradle): shared build conventions.
- [LWJGL3ify installation](https://github.com/GTNewHorizons/lwjgl3ify#usage): modern
  Java runtime support through launcher patches, RetroFuturaBootstrap, and UniMixins;
  separate dedicated-server forgePatches launcher. README recommends Hodgepodge for
  compatibility. Hybrid servers are outside the documented supported setup.

These moving URLs are discovery references. Pin exact revisions, downloads, hashes,
and compatible dependency versions during implementation. Native modern-bytecode
support is verified from template documentation; the precise Java 25 compiler,
ASM/Mixin, remapping, client, and server combination is NOT yet verified. Do not
assume changing one property proves Java 25 support. Distinguish the Gradle JVM,
compiler toolchain, bytecode target, test JVM, and game JVM.

Local baseline at handoff: branch main, clean before this document was added,
mod_version 0.2.1, Minecraft 1.21.1, NeoForge 21.1.77, Java 21. Recheck live state.
Older cross-version and Cleanroom documents contain historical snapshots and are
context, not current validation or restrictions on this newly authorized port.

## Non-negotiable behavior

- STANDALONE stays primary and default: Discovery plus plain WS on server.publicPort.
- Plain WS and unsigned Discovery remain supported by default. TLS, signatures,
  and downgrade enforcement are optional operator controls.
- EXTERNAL_PROXY may terminate WSS externally; the private hop remains plain WS.
  Do not implement certificate management in the mod.
- Discovery v1 is the normal route. Legacy probing is allowed only when Discovery
  is unavailable and legacyFallback is enabled. Invalid manifests and unsupported
  required filters must not silently connect to an unadvertised endpoint.
- Preserve wstunnel 10.7.1 and the Windows/Linux x86-64 support boundary, download
  verification, binary hashes, provenance, and licenses.
- Do not port the existing ZstdNet 1.4.7/1.4.8 adapters by assumption. Initially
  exclude their classes and Mixin from this platform and advertise no ZstdNet
  capability. Keep unsupported required ZstdNet filters fail-closed.

## Step 1: isolate implementation and record the baseline

1. Read applicable AGENTS.md files, run git status --short --branch, and inspect
   the diff before editing. Record the actual source revision and source version.
2. Prefer a separate platform directory with its own Gradle wrapper/build under
   platform-forge-1.7.10/. Keep the root NeoForge build intact. Inspect any existing
   migration work before choosing layout; do not overwrite another port.
3. Port only the needed shared code or introduce a narrowly scoped shared-source
   boundary. Do not turn this into an unsolicited migration of every platform or
   force the existing root build onto the GTNH toolchain.
4. Keep mod_version in the root gradle.properties as the version source of truth.
   Wire the platform's project.version to it and disable conflicting automatic
   Git-tag versioning through the supported GTNH mechanism. No version bump is
   necessary merely to begin the port.
5. Ignore new build/run/cache directories and keep local paths, logs, generated
   configuration, identities, and trust stores out of repository documents.

Pass: isolated layout, reproducible baseline record, existing source preserved.

## Step 2: prove the Java 25 platform before migrating the full mod

1. Download and inspect the official starter. Record its resolved version/revision
   and SHA-256; preserve MiguelNetwork's Apache-2.0 license and third-party notices.
   Do not blindly inherit example publishing workflows or credentials settings.
2. Use the starter's checked-in wrapper and supported extension points such as
   addon.gradle[.kts]. Inspect the pinned convention implementation to configure
   enableModernJavaSyntax=modern with Java 25 correctly; avoid an unexplained
   forceToolchainVersion override or reliance on a changing default.
3. Verify wrapper/Gradle compatibility with its launch JVM and configure Java 25
   compilation, tests, and game runs. Do not replace the wrapper with system Gradle.
4. Pin compatible LWJGL3ify, RetroFuturaBootstrap, UniMixins, and any actually
   required compatibility components. Check Java 25 class-file support throughout
   ASM, Mixin processing, and reobfuscation. Modern mod bytecode should be major 69.
5. Implement minimal Forge 1.7.10 metadata and mod entry point. Enable the template's
   supported early Mixin loading mechanism for vanilla networking classes.
6. Launch a minimal development client and dedicated server on Java 25. Then load
   the packaged production JAR in separate installed client/server instances using
   the documented modern-Java launcher setup.

Pass: both development and production instances load on Java 25, with exact
versions and commands recorded. A compiler-only success does not pass this gate.
If dependencies fail, capture the first concrete error, inspect the wrapper/JDK/cache,
and resolve the cause before repeating builds. Complete independent work when a
runtime environment is unavailable, but report that gate as pending.

## Step 3: port configuration, lifecycle, and reusable transport code

1. Replace NeoForge entry point/events/configuration/paths/ModList queries with
   actual Forge 1.7.10 equivalents. Keep client-only classes off dedicated-server
   loading paths. Preserve option meanings and document any necessary file-format
   change with exact resulting keys and defaults.
2. Adapt core/, discovery/, ClientDiscoveryService, ClientTrustStore,
   ClientTransportProbe, StandaloneGateway, and supporting classes. Their modern
   JDK APIs can remain where verified on Java 25, including virtual threads,
   HttpClient, records, ProcessHandle, and optional Ed25519 signing.
3. Remove implicit dependence on NeoForge-provided Gson/SLF4J. Inspect the actual
   runtime classpath. Supply compatible adapters or properly relocated dependencies
   where necessary; verify record serialization and existing JSON parsing APIs.
4. Adapt ServerTunnelController and DiscoveryDocumentProvider to obtain real
   server bind/port and directories, with readiness and stop behavior preserved.
5. Carry over pinned native staging, checksum verification, notices, and both
   bundled and no-native distribution behavior where present in the source.
6. Retain optional signing/trust and download configuration without introducing
   mandatory TLS or signatures. Do not copy live trust material into fixtures.

Pass: meaningful existing protocol/process/gateway tests run on Java 25, server
publishes valid Discovery and accepts plain WS, and shutdown cleans up sidecars.

## Step 4: implement and validate 1.7.10 connection interception

1. Inspect the actual decompiled and mapped 1.7.10 NetworkManager connection
   creation path and its callers. Determine exact signatures and descriptors;
   do not copy Connection.connect from 1.21.1 or a descriptor from 1.12.2.
2. Capture the original host/port context early enough for Discovery, routing,
   server-list status, DNS/SRV behavior where applicable, and the Minecraft/FML
   handshake. Redirect only the socket destination to the local tunnel endpoint.
3. Ensure one route decision/redirection per connection, no recursive tunneling,
   and no rewrite of the original handshake host, port, or FML marker.
4. Verify early Mixin registration, annotation processing, refmap/mapping output,
   and reobfuscated production targets. Select a Mixin compatibility level that
   the pinned runtime actually supports; do not guess a JAVA_25 setting.
5. Exercise status ping, direct connect, server-list join, login, cancel, disconnect,
   reconnect, server restart, and connection failure. Ensure client tunnel cleanup
   does not break concurrent status queries or a live gameplay connection.

Pass: a production client joins a production Forge 1.7.10 server through Discovery
and the standalone WS gateway, preserves FML handshake behavior, and transfers
actual gameplay data on Java 25.

## Step 5: regression and runtime acceptance matrix

Record PASS, FAIL, or NOT RUN with evidence for each applicable row. Do not convert
missing environments into a claimed pass or weaken behavior to satisfy a test.

| Area | Required checks |
|---|---|
| Default deployment | Unsigned Discovery, plain WS, one public port, actual client join |
| Discovery routing | Valid manifest; invalid manifest; unknown required filter; unsupported ZstdNet filter |
| Fallback | Discovery unavailable with legacyFallback enabled and disabled; no bypass for invalid/unsupported manifests |
| Lifecycle | Ping/join/cancel/disconnect/reconnect; server restart; process exit and startup failures |
| Gateway | Concurrent clients, bidirectional traffic, half-close, network interruption, port conflicts, no lingering processes |
| Optional controls | Signed Discovery/trust persistence and verification failures; external WSS valid and invalid certificate paths |
| Packaging | Production mapping/Mixin loading, exact Minecraft range, Java bytecode, no NeoForge classes/metadata |
| Platforms | Windows x86-64 and Linux x86-64 native selection, hashes, startup, transfer, shutdown |
| Existing platform | NeoForge wrapper tests/build if shared code or root build logic changed |

Default-path success must include these exact existing diagnostics:

```text
standalone WS gateway is ready
Discovery and wstunnel share one port
selected Discovery route
```

Discovery unavailable, selected legacy ... route, or discovered vanilla TCP endpoint
indicates fallback entry/use and is not evidence of successful default Discovery.
Use synthetic test data and dedicated run directories; never overwrite user server
files or fabricate agreement to a Minecraft EULA. Request any genuinely required
user action while continuing independent work.

## Step 6: documentation, production artifact, and handoff

1. Document the pinned Java 25 client and server installation recipes, required
   components, supported OS/architecture, exact config/log diagnostics, default
   standalone deployment, optional WSS, and absent ZstdNet integration.
2. Document reproducible wrapper commands and whether the production artifact is
   reobfuscated by jar or a separate task in the chosen template. Inspect the
   actual JAR rather than assuming successful compilation means it is distributable.
3. Use an unambiguous platform name such as
   miguelnetwork-forge-1.7.10-<mod_version>.jar. Preserve required native payloads,
   notices, and licenses. Verify resource hashes and archive contents. If producing
   a no-native distribution, verify its disclosure and download behavior separately.
4. Create docs/FORGE_1_7_10_JAVA_25_VALIDATION.md with exact dependency pins,
   source revisions, build/test outcomes, runtime matrix, and unresolved issues.
   Keep raw logs outside Git and sanitize any excerpts.
5. Run git diff --check and inspect the final diff. Report exact artifact paths
   and SHA-256 in the chat, plus any incomplete environment-specific validation.
6. No commit, tag, push, publication, GitHub login, or release creation.

## Definition of done

The goal is achieved when the isolated port builds reproducibly; the packaged
production client and server run on Java 25 and communicate through the default
Discovery/WS path; the required matrix is verified; packaging and hashes are
checked; and installation/validation documentation is complete. A source-only
port, passing unit tests, or successful development launch is partial progress.
If external access or user action prevents completion, identify the exact missing
gate and preserve all completed work without claiming runtime support prematurely.
