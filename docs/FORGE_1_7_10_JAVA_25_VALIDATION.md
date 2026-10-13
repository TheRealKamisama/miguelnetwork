# Forge 1.7.10 / Java 25 validation

The earlier platform baseline below is historical. Updated maintained ZstdNet
builds now support the native API; see [current native validation](NATIVE_INTEROP_V1_VALIDATION.md)
for the accepted artifact hashes, paired Minecraft runs and remaining scope.

Updated 2026-10-04. **Acceptance complete for the agreed Windows scope.** The
current production JAR passes development and installed client/server loading,
Discovery/plain-WS gameplay, lifecycle and restart checks, optional signed
Discovery and WSS checks, unit/native integration tests, and packaging verification.
The user explicitly excluded Linux runtime testing on 2026-10-04. Its binary
selection and hashes are verified; Linux runtime behavior remains untested.

## Source and toolchain baseline

- Original source: `71da552d24b58227390a2f9aa5de6c36e122fbfd`, root mod version `0.2.1`.
- Working branch: `codex/forge-1.7.10-java25`; no commit, tag, push, or publication.
- Existing NeoForge implementation and root build logic are unchanged. The only
  pre-existing untracked file was the migration handoff document; it is preserved.
- Official ExampleMod1.7.10 starter: `master-packages` resolved to
  `6453dec5f5f534e604887d3505ddf7125889c6f9`. Download digest matches the release
  asset's published SHA-256.
- GTNHGradle `2.0.33`, source tag `15c83faa7b086dba4b0efcf2212011b84dbade4c`.
  The native `modern` branch in its `ToolchainModule` selects Java 25. Git version
  inference is disabled using `gtnh.modules.gitVersion=false`; `addon.gradle`
  reads `mod_version` from the root properties.
- Starter wrapper: Gradle `9.7.1`, verified distribution SHA-256
  `acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a`.
- Gradle daemon, mod compiler, test JVM, and game JVM: Azul Zulu
  `25.0.4.1+1-LTS` / `25.36+205-CA` on Windows x86-64. Historical Minecraft and
  launcher compilation uses RetroFuturaGradle's Java 8 toolchain.
- RetroFuturaGradle `2.0.5`, Forge `10.13.4.1614`, Minecraft `1.7.10`, stable MCP
  mappings `12`. Ancillary FML mappings URL is pinned to
  `a099592d3d1418245e3af65eda195da244287188`.
- LWJGL3ify `3.0.33`, bundled RetroFuturaBootstrap `1.1.1`, client LWJGL `3.4.2`.
- UniMixins `0.3.1`, UniMix `0.15.6+mixin.0.8.7`, shaded ASM `9.9.1`.
  LWJGL3ify forgePatches bundles ASM `9.10.1`.
- Gson `2.13.2` is shaded and relocated; Log4j uses the Forge runtime API.
- Native wstunnel stays at `10.7.1`; both native resources retain the original
  manifest's archive and executable hashes and licensing.

Download URLs and SHA-256 pins are recorded in
[`toolchain-lock.json`](../platform-forge-1.7.10/toolchain-lock.json). The official
Forge Maven endpoints returned HTTP 403 for installer/universal downloads. The
OvermindDL1 mirror already used by GTNH supplied the universal JAR; its SHA-1
matched the pinned LWJGL3ify launcher manifest
(`25fd97f72beca728112256938e03e8105b1b78cc`). Installed client libraries were
selected from all applicable LWJGL3ify launcher patches and checked against their
published hashes. Vanilla client and server JAR SHA-1 values were also verified.

## Compatibility decisions established by running the stack

`JAVA_25` exists in UniMixins 0.3.1 but failed at runtime: its shaded ASM has no
implementation-version manifest section, so Mixin reports ASM 9.0 and rejects
Java 25 compatibility. The three small connection/status/cancellation Mixins use the
starter's supported separate Mixin source set with `--release 8` and `JAVA_8`.
All main mod classes remain native Java 25 / major 69; the bridge classes are
major 52. This uses neither Jabel nor JVM Downgrader. Production loading and the
actual networking hook are tested, rather than inferred from compilation.

The default modern development task included optional Hodgepodge `2.7.206`.
Windows blocked its transitive `GTNHExtLib 1.0.4` download as virus/potentially
unwanted software. No protection was disabled. Dependency inspection showed that
LWJGL3ify does not require Hodgepodge/GTNHLib, so this minimal platform excludes
Hodgepodge from development runs. Additional modpacks need their own compatibility
validation.

## Build and packaging evidence

Commands run from the platform directory, using its wrapper:

```text
gradlew.bat test verifyDistribution
gradlew.bat test verifyDistribution verifyNoNativeDistribution reobfRuntimeProbe
gradlew.bat build reobfRuntimeProbe --no-daemon
```

The local native archive option was used with hash verification. `build-22.log`
records the latest full successful build: **58 tests, 57 passed, 0 failures/errors,
1 skipped**. The skipped test requires an operator-supplied external endpoint;
self-contained native transport tests ran. These include the exact unsupported
`zstdnet-stream` filter ID, two simultaneous sockets sharing a sidecar, eight
concurrent gateway streams, half-closes, resets, port conflicts, and sidecar
readiness/exit/timeout/interruption cleanup.

The new Netty ordering regression reproduces a successful connect future before
`channelActive` finishes, then confirms the returned future remains incomplete
until initialization finishes. Connection refusal also completes that future
with an error. Native/no-native payload, metadata, every connection/status refmap
target, and bytecode verification passed.

Earlier builds used a temporary intermediate-JAR filename override while a
development instance was running. That instance was stopped through Minecraft's
normal shutdown method. `build-22.log` records the ordinary wrapper build without
that override, and both production hashes remained unchanged.

An initial controlled recompilation reproduced the bundled JAR exactly but
exposed task-order-dependent `Multi-Release` metadata in the no-native
JAR. The no-native task now copies the production manifest bytes verbatim using a
ZIP repack, and verifies equality. A forced repack reproduced the fixed archive
(`no-native-reproducibility-fixed.json`). Finally, `reproducibility-result-03.json`
records matching before/after hashes for both final artifacts after recompiling
all mod and Mixin sources. The final no-native JAR also passed
`no-native-live-download-03.log`: refusal before opt-in, real proxied download,
archive/binary hash verification, verified reuse, and executable version 10.7.1.

The production task is `reobfJar`, not `jar`. The verifier checks the generated
SRG target of `NetworkManager.provideLanClient(InetAddress,int)`, the platform
metadata, class versions, relocated Gson, absence of NeoForge and ZstdNet adapter
classes, and absence of the test driver in the production JAR. The no-native JAR
is repackaged from the already reobfuscated artifact and preserves its bootstrap
manifest. Native hashes and required notices are checked separately.

## Runtime evidence

Raw logs and runtime files are outside Git. Every installed run below identifies
the current bundled SHA-256
`5f4386efe40362b767cf1b69dee0b5f3cb67486af1423821ff85067a7389b902`.

| Evidence | Result |
|---|---|
| `dev-client-04.log` / `dev-server-03.log` | Java 25 development client/server: status ping, cancel with no sidecar, two gameplay joins, disconnect/reconnect, and clean console shutdown |
| `production-result-20261004-215508.json` | Installed production default path: unsigned Discovery, plain WS, `legacyFallback=false`, FML login, gameplay ticks/chat traffic, cancel, disconnect/reconnect; both exit codes 0 |
| `production-result-20261004-220049.json` | Server stopped while client was playing; client detected disconnect, fresh server reached readiness, and client completed another gameplay join; both server processes and client exited 0 |
| `production-result-20261004-222924-signed.json` | Signed Discovery with client verification enabled: full gameplay sequence passed; existing server identity and client trust hashes unchanged (`signed-current-persistence.json`) |
| `production-result-20261004-223433-external-wss.json` | External TLS gateway: one Discovery request, three WS upgrades, zero TLS failures; status, cancel, two gameplay joins and shutdown passed |
| `production-result-20261004-220625-external-wss.json` | Deliberately untrusted WSS certificate: trusted HTTPS Discovery succeeded, zero WS upgrades, five `unknown_ca` TLS alerts, explicit status failure, no raw-TCP retry, no world/sidecar retained, both processes exited 0 |
| `headless-result-20261004-022315.json` | Default production server: fresh-nonce Discovery validation, native hash check and four concurrent actual Minecraft status/pong exchanges over WS |
| `headless-result-20261004-022342-signed.json` | Same headless checks with the published Ed25519 response signature verified |

The production acceptance driver is a separate test mod under `build/runtimeProbe/`.
Its PASS markers require actual Minecraft state, including player ticks and two
successful joins. It is absent from both distributable JARs. The development driver
was removed from `run/client/mods` after testing so ordinary development launches
do not auto-connect or exit.

The three early Mixins applied successfully in development and production. The
first gameplay join uses the server-list/status data; the second uses a fresh
server entry without a prior ping, exercising the direct reconnect path. The
Minecraft caller still constructs its original handshake host/port and FML marker;
only the Netty socket destination is redirected.

Required default-route diagnostics were present:

```text
standalone WS gateway is ready
Discovery and wstunnel share one port
selected Discovery route
```

No legacy-route diagnostic is counted as a successful default Discovery test.
The native fallback integration tests separately disabled Discovery and verified
that explicitly enabled legacy WS fallback transfers data. Invalid manifests and
required unsupported filters reject without entering that path.

The user accepted the Minecraft EULA for the isolated test instances. Servers bind
to loopback and use offline mode for synthetic test players; internet account
authentication was not part of this transport validation. The external TLS fixture
trusted its synthetic CA only in the test JVM and sidecar processes, without
modifying OS trust. Audio fell back to silent mode, and Forge's legacy update
checker received invalid JSON; neither prevented the accepted gameplay runs.

All migration test game instances and sidecars were stopped after validation.

## Acceptance matrix

| Requirement | Status | Evidence / remaining scope |
|---|---|---|
| Isolated platform, root version source, NeoForge preserved | PASS | Independent wrapper/build; root source/build unchanged |
| Native Java 25 compiler/test stack | PASS | Compiler/test JVM and class major 69; three explicit major-52 Mixin bridges |
| Development client loading and gameplay | PASS | `dev-client-04.log`, complete driver sequence on Java 25 |
| Development server loading/shutdown | PASS | `dev-server-03.log`, normal console stop and successful wrapper exit |
| Current installed production server loading/shutdown | PASS | Both unsigned and signed headless production runs |
| Installed client status/FML login/gameplay | PASS | Current production default, signed and WSS runs |
| Unsigned Discovery, WS, one public port | PASS | Current native integration and real Minecraft status/pong transport |
| Invalid/oversized manifests never fall back | PASS | Routing rejection tests with fallback enabled |
| Unknown required and required `zstdnet-stream` filters | PASS | Exact-ID routing tests; no adapters packaged |
| Discovery unavailable, fallback disabled | PASS | Routing policy test |
| Discovery unavailable, fallback enabled | PASS | Native integration selects legacy WS and transfers bytes |
| Concurrent socket leases and disconnect cleanup | PASS | Two real sockets share a native tunnel; closing one retains the other |
| GUI ping/join/cancel/disconnect/reconnect | PASS | Development and installed driver sequences; cancel leaves no sidecar |
| Live-server restart/reconnect | PASS | `20261004-220049`, real interruption and successful second join |
| Sidecar startup/early-exit/timeout/interruption | PASS | Process lifecycle tests, children reaped |
| Gateway concurrency, bidirectional traffic, half-close/reset/conflict | PASS | Eight concurrent streams and explicit lifecycle tests |
| Current signed Discovery producer | PASS | Headless production response signature verified |
| Client identity/trust persistence and verification failures | PASS | Current signed gameplay run preserves identity/trust; codec/trust rejection tests |
| External WSS with trusted certificate | PASS | `20261004-223433-external-wss`, full gameplay sequence |
| Untrusted-certificate status failure | PASS | `20261004-220625-external-wss`, explicit failure, no downgrade/sidecar |
| Production mapping / exact Minecraft range / bytecode | PASS | Metadata, all connection/status refmap targets, and class checks |
| Client early-Mixin runtime application | PASS | All three bridges applied in development and production; actual route/failure behavior verified |
| Bundled native payloads and notices | PASS | Both packaged native hashes and legal/provenance files checked |
| No-native disclosure and acquisition | PASS | Archive checks, acquisition tests, and live opt-in download smoke check |
| Windows x86-64 native startup/transfer/shutdown | PASS | Current development, production gameplay and headless integration runs |
| Linux x86-64 native selection/hash | PASS | Selection tests and packaged binary hash |
| Linux x86-64 startup/transfer/shutdown | NOT RUN | Explicitly excluded from completion criteria by the user on 2026-10-04; runtime untested |
| NeoForge regression build | NOT RUN | Not required: root source/build logic unchanged |

All required checks are complete under the user-approved Linux runtime exclusion.
Linux runtime support is not claimed as verified.

## Verified repairs

The certificate-failure diagnostic run showed a closed status manager being removed
without a disconnect reason, leaving the UI at `Pinging...`. Netty 4.0 also marks
its connect promise successful before Minecraft's `channelActive` initialization;
a later observation caught a null `NetworkManager.channel()` immediately after
`provideLanClient` returned. The candidate now completes its exposed future after
that initialization, reaps sidecars off Netty's event loop, and supplies an EOF
reason for a closed routed connection if Minecraft has not published one.

Each status query records whether it used Discovery. Minecraft's pre-1.7 raw TCP
status retry is suppressed for that query, so a route failure cannot bypass the
advertised transport. This decision belongs to the query and does not depend on
the Discovery cache's expiration time. Required unsupported filters remain rejected.

Plain WS selection is logged at INFO, consistent with its first-class default
status; it is not treated as a security warning.

The previous live-restart failure was intermittent: a later diagnostic run
`20261004-001258` completed a real server stop/restart and a second gameplay join
on the earlier artifact. The current production artifact subsequently passed the explicit live-restart
run `20261004-220049`. The certificate-failure repair passed
`20261004-220625-external-wss`, replacing the former perpetual `Pinging...` state
with an explicit failure and complete tunnel cleanup.

## Current artifacts

Paths are relative to the repository root. These are locally verified production artifacts; nothing has been published.
The bundled hash identifies the JAR used for all current installed Windows tests.

| Artifact | SHA-256 |
|---|---|
| `platform-forge-1.7.10/build/libs/miguelnetwork-forge-1.7.10-0.2.1.jar` | `5f4386efe40362b767cf1b69dee0b5f3cb67486af1423821ff85067a7389b902` |
| `platform-forge-1.7.10/build/libs/miguelnetwork-forge-1.7.10-0.2.1-no-native.jar` | `fbc676697396237d4148792388d8be1889a9ac3d6fe261a0b81ebc5d2dadf25f` |

`git diff --check` and a whitespace scan of the new platform text files passed.
A scoped Git diff confirmed no change to the root source, Gradle build, wrapper,
or version properties. The root README only links to the new platform.

The older deferred development server was stopped normally on resumption. The
fresh development run also stopped through its console, saved its test world,
reaped its sidecar, and returned a successful wrapper result. No pending shutdown
approval or outstanding validation blocker remains in the agreed scope.
