# Cleanroom 1.12.2 migration

Date: 2026-09-17. Branch: `codex/cleanroom-1.12.2`, based on NeoForge `main` at `85beadd`.
Version remains `0.2.0`; this is development work, not a release or a completed runtime compatibility claim.

## Selected platform

- Minecraft 1.12.2, MCP stable 39-1.12.
- Cleanroom 0.6.13-alpha, confirmed as the latest official release on the date above.
- Java 25. Local build JVM: Azul Zulu 25.0.4.1+1 (25.36+205), Windows x86-64.
- MCP merge helper: Azul Zulu JDK 8u504 x86-64. Only the legacy build helper runs on Java 8;
  the Mod and game target Java 25. Foojay provisioning and explicit local toolchains are documented in DEVELOPMENT.md.
- Gradle wrapper 9.7.0, SHA-256 `84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae`.
- Unimined 1.4.36-kappa and Shadow 9.5.1, following the official Mixin template's current toolchain.
- Cleanroom dependency is restricted to the selected version until additional versions are tested.

The official template still selected Cleanroom 0.6.10-alpha; this branch selects 0.6.13-alpha as requested.
It does not use the historical Java 21 / Cleanroom 0.4.4-alpha template.

## Implemented changes

- Replace NeoForge entry/config/paths with 1.12.2 FML lifecycle and startup-only TOML configuration.
  Reuse the loader-independent SettingsSpec from the local Fabric port, with its configuration tests.
- Keep `mod_version` as the metadata source, generate BuildInfo and mcmod.info, and package only Cleanroom metadata.
- Adapt the client connection at NetworkManager's Netty connect call. Both address and port are replaced together,
  once per connection. The caller's Minecraft handshake address and Forge marker are left untouched.
- Add a client-only Minecraft shutdown Mixin and retain idempotent server/JVM sidecar cleanup.
- Preserve Java virtual threads, standalone plain WS, Discovery v1, optional signatures and optional proxy-terminated WSS.
- Remove ZstdNet compatibility classes, Mixin, version tests, internal-loopback composition tracking and server route/port discovery.
  This platform implements no additional transport filters; unknown required filters reject the route.
- Make `legacyFallback=false` reject Discovery unavailability, and classify oversized Discovery documents as invalid data.
- Shade NightConfig into an internal namespace; do not bundle another copy of Minecraft/Netty/Mixin/ASM.
- Verify production metadata, Mixin selectors, Java 25 class format, isolated TOML parser, native hashes and notices.
- Change CI to Java 25 and production-JAR upload. Release workflow performs manual build preparation only, without publishing.

## Verification status

The wrapper `build --no-daemon` completed successfully on Windows x86-64 / Java 25.
The final build selected the local Java 8 helper with `-Porg.gradle.java.installations.fromEnv=JAVA_HOME,JAVA8_HOME`
and used checksum-verified local wstunnel archives through `-PwstunnelArchiveDir`.

- 40 tests discovered: 39 passed, 0 failed, 1 skipped. The skipped remote endpoint probe requires
  `MIGUELNETWORK_TEST_ENDPOINT`; no public endpoint was supplied.
- The real bundled Windows wstunnel integration test passed unsigned Discovery with `legacyFallback=false`,
  128 KiB arbitrary-byte echo through the standalone WS gateway, sidecar reuse, two start/stop cycles,
  reconnect after restart, and closure of the local proxy and gateway listeners.
  All test listeners were bound to loopback, using temporary configuration and an echo backend.
- `verifyDistribution` passed on the production remap: Cleanroom metadata/manifest, Java 25 class major 69,
  client-only Mixin configuration/refmap, relocated NightConfig, bundled library sources/licenses,
  and both pinned Windows/Linux native hashes.
- Actual Cleanroom NetworkManager bytecode has exactly the targeted `Bootstrap.connect(InetAddress,int)` call
  in `createNetworkManagerAndConnect`. The generated production refmap resolves this method to `func_181124_a`
  and `shutdownMinecraftApplet` to `func_71405_e`. This is bytecode inspection, not a live Mixin transformation test.
- `git diff --check` passed. Gradle reports deprecated plugin/build API usage for a future Gradle 10 upgrade;
  the pinned Gradle 9.7.0 build succeeds.

Artifact: `build/libs/miguelnetwork-cleanroom-1.12.2-0.2.0.jar` (10,414,492 bytes).

SHA-256: `cfb4d85c697c9b33d980d828fb16970f5babc7c7c212a146ad5cd2e205b6e38f`.

Initial validation exposed a missing MCP Java 8 helper, missing Gson in the plain JUnit runtime, and an incorrect
shutdown-method name in the distribution checker. All three were corrected. Test-only Gson 2.14.0 and Log4j 2.26.0
match the selected Cleanroom installer; those game libraries are not bundled into this Mod.

Environment preparation verified Java 25 and the official Gradle checksum. Direct Gradle/GitHub downloads timed out;
the official asset was recovered using ranged downloads and checked against the full official SHA-256. A public mirror
download also matched the same official hash; the wrapper cache uses the verified official asset.

Outstanding runtime gates include installed production-JAR status/login/cancel/reconnect, hostname/SRV and Forge marker
preservation, live Mixin application, dedicated-server side isolation, Linux sidecar behavior, optional proxy WSS,
and real target-modpack compatibility. CI has been configured but has not been executed remotely.
Do not treat a Java compile or remap success as those runtime tests. No EULA acceptance, game-world migration or release
publication has been performed.

## Evidence

- [Latest selected Cleanroom release](https://github.com/CleanroomMC/Cleanroom/releases/tag/0.6.13-alpha).
- [Official Mixin template](https://github.com/CleanroomMC/CleanroomModTemplate/tree/mixin).
- [Selected NetworkManager patch](https://github.com/CleanroomMC/Cleanroom/blob/0.6.13-alpha/patches/minecraft/net/minecraft/network/NetworkManager.java.patch).
- [Build instructions](DEVELOPMENT.md), [runtime instructions](USER_GUIDE.md), [release gates](RELEASE_CHECKLIST.md).

Historical NeoForge and Forge validation remains historical. There is no ZstdNet compatibility work in this migration.
