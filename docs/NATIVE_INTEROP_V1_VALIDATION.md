# Native interoperability v1 validation

Validated on Windows x86-64, Java 25, on 2026-10-06. This records the native
protocol implementation in the maintained Forge 1.7.10 and Cleanroom 1.12.2
pairs. It supersedes earlier compatibility-adapter/runtime checkpoints for this
scope. The implementation is local and uncommitted; no release was published.

## Accepted behavior

Both peers negotiate API major 1, the exact Minecraft version, capabilities,
and stream placement through their public JDK callback descriptors. Each
acknowledges compression before encapsulation outbound and the reverse inbound:

```text
Minecraft -> ZstdNet -> MiguelNetwork -> WS/WSS -> MiguelNetwork -> ZstdNet -> Minecraft
```

WebSocket framing and transport stay exclusively in MiguelNetwork. The legacy
ports have no cross-mod compatibility Mixin or private-field adapter. Vanilla
Minecraft hooks remain. Plain WS, unsigned Discovery and STANDALONE remain the
default deployment. The full API is in [NATIVE_INTEROP_V1.md](NATIVE_INTEROP_V1.md).

## Final checks

| Check | Forge 1.7.10 | Cleanroom 1.12.2 |
| --- | --- | --- |
| MiguelNetwork unit/integration suite | PASS: 65 passed, 1 skipped | PASS: 50 passed, 1 skipped |
| ZstdNet unit/integration suite | PASS: 54 passed | PASS: 41 passed |
| Paired production-JAR stream fixture | PASS: 41 checks | PASS: 41 checks |
| Actual production Minecraft client/server | PASS | PASS |
| Native status and cancelled-connection cleanup | PASS | PASS |
| Gameplay and reconnect | PASS: two joins | PASS: three joins |
| Native statistics used by the HUD | PASS | PASS |
| Report response and generated HTML | PASS | PASS |
| Production packaging and public bridges | PASS | PASS |
| Source scope and whitespace audit | PASS | PASS |
| Linux runtime | NOT RUN | NOT RUN |

All suites have zero failures/errors. The two skips require an external endpoint
and do not count as passes. There are no unresolved failures in the final runs.

The byte-stream fixtures use real bundled wstunnel 10.7.1 and JNI. They cover
both mod startup orders, explicit order agreement/rejection, effective bound
endpoints, live restriction reload acknowledgement, route withdrawal, original
handshake and Forge marker, concurrent status/play leases, RAW adoption,
statistics, cancellation, reconnect and sidecar cleanup. They do not substitute
for the separately completed Minecraft loader runs.

The optional-filter regressions prepare an advertised unfiltered tunnel without
a ZstdNet peer. Required unsupported filters and malformed manifests still
reject. A short, still-open Zstd handshake regression verifies the Cleanroom
classifier fix.

## Build and runtime scope

Builds use each module's pinned wrapper. Completed tasks:

- MiguelNetwork Forge: `test reobfJar verifyPlatform verifyDistribution verifyNoNativeDistribution`.
- MiguelNetwork Cleanroom: `test build`, including `verifyDistribution`.
- ZstdNet Forge: `test reobfJar`.
- ZstdNet Cleanroom: `test build verifyArtifact verifyPackagedNative`.

Separate acceptance probes are excluded from the distributions. The Forge
loader uses Forge 10.13.4.1614, LWJGL3ify 3.0.33 and UniMixins 0.3.1. The
Cleanroom loader uses 0.6.13-alpha. Both actual game runs use Java 25,
disposable loopback installations, plain WS, unsigned Discovery and
`legacyFallback=false`. Existing installations/worlds/configs were preserved.

The isolated Cleanroom fixture confirms its startup query and disables only
the optional Fugue/Scalar presence warning. It is not acceptance of arbitrary
modpacks. The probes verify readable HUD statistics and generated report HTML;
visual HUD layout, clipboard interaction and opening the OS browser are NOT RUN.

Native paired WSS through an external TLS gateway, native signed-Discovery
deployments, LAN sharing, sustained load, Linux runtime and actual voice-service
integration are NOT RUN in this acceptance. Existing generic transport tests
and static native-resource checks do not establish those runtime results.
Direct UDP destination separation is covered by tests; UDP is not carried by WS.
The Forge no-native JAR has packaging acceptance only in this run.

Earlier fixture failures were resolved: the Forge report receiver must be
started through the client command and the fixture must scan per-server report
subdirectories. Final production artifacts passed the corrected probes.

## Accepted artifact hashes

Paths below are relative to the corresponding repository checkout. Both mods
must be updated together; an older JAR with the same release label lacks this API.

| Module | Production artifact |
| --- | --- |
| MiguelNetwork Forge | `platform-forge-1.7.10/build/libs/miguelnetwork-forge-1.7.10-0.2.1.jar` |
| ZstdNet Forge | `mods/1.7.10/zstdnet-forge/build/libs/zstdnet-1.7.10-forge-845b490-codex-native-interop-forge-1-7-10+845b490424-dirty.jar` |
| MiguelNetwork Cleanroom | `build/libs/miguelnetwork-cleanroom-1.12.2-0.2.0.jar` |
| ZstdNet Cleanroom | `mods/1.12.2/zstdnet-cleanroom/build/libs/zstdnet-1.12.2-cleanroom-1.4.7-cleanroom-alpha.1.jar` |

SHA-256:

```text
MiguelNetwork Forge
038551c4ce2b7f67f01883433c8f3f779c52e123646a63cd373afd55e5a15d47
ZstdNet Forge
9d50f1bb9b362c68d47c16bf2132d341232a20557f90b32060be97544399ca9a
MiguelNetwork Cleanroom
eef7b72c55396ad70f057d27fa6f4afb3a8b00ecfa1e812a8e6823b600881d75
ZstdNet Cleanroom
eac338cd424383942cab08b20e4051b6dbfbb674148b28390cae05d5d0e5ca9b
MiguelNetwork Forge no-native
777490c44143820ffab5154b32b823772bfc86f0db2c4c81577fb79422b9b10a
```

The supplemental Forge no-native file is
`platform-forge-1.7.10/build/libs/miguelnetwork-forge-1.7.10-0.2.1-no-native.jar`.
Its manifest matches the production manifest byte-for-byte and it contains no
bundled native binaries. The packaged Cleanroom Zstd JNI JAR SHA-256 remains
`d4725bce2c3e4f3df54cbea8bf5966e240872bb7ba7658572ededaef28a01d83`.
Pinned wstunnel/native-resource verification passes. Production JARs contain
the public bridge/order classes and exclude acceptance probes, test stubs and
the removed Cleanroom adapter.
