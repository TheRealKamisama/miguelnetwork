# Forge 1.20.1 build verification

Date: 2026-09-16. Version: 0.2.0. Base: `6d2416f` (merged Forge port).

## Scope

The maintainer reported that the original fork behaved normally in-game and
explicitly excluded additional game testing from this task. This verification
covers compilation, automated loopback tests, and the production JAR. It does
not claim that the revised JAR has undergone another game session or a Linux run.
Historical NeoForge results in `VALIDATION_REPORT.md` remain historical evidence.

## Build result

- Checked-in Gradle wrapper: 8.14.5, with its official distribution SHA-256 pinned.
- Build and test JVM: Microsoft OpenJDK 17.0.20.1, Windows x86-64.
- Minecraft: exactly 1.20.1; Forge compilation baseline: 47.1.3.
- Command: `gradlew.bat build --no-daemon` (local JVM/toolchain/network settings
  were supplied outside the repository).
- Result: successful, including `compileJava`, `reobfJar`, `test`, and
  `verifyDistribution`; 10 actionable tasks executed.
- Tests: 35 total, 34 passed, zero failures/errors, one skipped. The skipped
  `ClientTransportProbeTest.detectsConfiguredRealEndpoint` requires the optional
  `MIGUELNETWORK_TEST_ENDPOINT` environment variable, which was not set.
- All five `StandaloneGatewayTest` cases passed, covering Discovery, Upgrade
  forwarding, concurrent bidirectional traffic and half-close, active tunnel
  shutdown, and the 128-connection limit with recovery after slots are freed.

Initial environment failures were a direct Gradle distribution download timeout
and a missing Java 17 toolchain with unsuccessful automatic provisioning. The
official distribution checksum and a locally installed Java 17 toolchain were
verified before retrying. These failures were not source compilation failures.

## Production artifact

Path relative to the repository:
`build/libs/miguelnetwork-forge-1.20.1-0.2.0.jar`

Size: 10,043,743 bytes.

SHA-256:
`8AE534F5195AD592247B722EA3438DFA29450408DDBBE085838BF55FAEF90E36`

The build checks the reobfuscated production artifact, not `build/devlibs/`:

- `META-INF/mods.toml` is present, NeoForge metadata is absent, the Minecraft
  dependency is `[1.20.1]`, and the Mod version is `0.2.0`.
- `META-INF/MANIFEST.MF` registers `MixinConfigs: miguelnetwork.mixins.json`.
- The Mixin JSON uses `JAVA_17` and references `miguelnetwork.refmap.json`.
- The refmap maps the explicit three-argument `Connection.connect` descriptor to
  SRG `m_290025_`, retaining the `ChannelFuture` return type.
- Every packaged class is compatible with Java 17 (major version at most 61).
- Both pinned wstunnel 10.7.1 native executables match the manifest hashes, and
  required license, notice, and provenance files are present.

The CurseForge workflow now resolves Minecraft 1.20.1 in `minecraft-1-20` and
uses Java 17. Publishing was not executed. No game, release, tag, or remote push
is part of this verification.
