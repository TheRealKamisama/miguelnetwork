# Cleanroom development

Target: Minecraft 1.12.2, Cleanroom 0.6.13-alpha, Java 25. Build using the checked-in Gradle 9.7.0 wrapper and
Unimined 1.4.36-kappa, MCP stable 39-1.12. `mod_version` in `gradle.properties` is the version source of truth.

Keep `JAVA_HOME` on JDK 25. The historical MCP merge step also requires a JDK 8 build helper;
this does not change the Mod's Java 25 compile target or game runtime. The Foojay settings plugin can provision
the helper automatically. For offline/local provisioning, set `JAVA8_HOME` to an installed x64 JDK 8 and pass
`-Porg.gradle.java.installations.fromEnv=JAVA_HOME,JAVA8_HOME` to the wrapper. Both JDKs must be available to CI.

```text
.\gradlew.bat --version
.\gradlew.bat test
.\gradlew.bat build --no-daemon
.\gradlew.bat runClient
.\gradlew.bat runServer
```

Use `./gradlew` on Linux. Game runs need a complete Cleanroom installation and any upstream-required components.
Normal server EULA acceptance remains an operator action. Do not change an existing world's files for testing.

`build` must produce and verify the `remapShadowJar` artifact, not a development JAR.
It contains `mcmod.info`, `ModType: CRL`, `MixinConfigs`, client-only JAVA_25 Mixin configuration,
the shaded NightConfig parser and both pinned native binaries with their legal/provenance files.
The default CleanMix phase registers Minecraft mixins; there is no ZstdNet hook or unnecessary coremod transformer.

The connection injection targets `NetworkManager.createNetworkManagerAndConnect(InetAddress,int,boolean)` and changes
the Netty connect argument pair once. Minecraft handshake parameters and the Forge marker remain with the original caller.
Check transformed production bytecode and a real status/login/reconnect session before declaring runtime compatibility.

Tests cover base routing/filter rejection, startup-only TOML validation, Discovery, signatures/trust, command construction,
native extraction, backend resolution and loopback gateway behavior. Optional remote endpoint tests require
`MIGUELNETWORK_TEST_ENDPOINT`. A skipped optional test must be recorded.
The standalone integration test starts the real bundled wstunnel on loopback, checks byte forwarding/reuse/restart,
and stops both sidecars afterward. It uses temporary files and does not start Minecraft or accept its EULA.

Keep default STANDALONE/plain WS behavior. Unknown required filters, invalid manifests, or Discovery unavailability with
`legacyFallback=false` must not trigger a silent direct connection. Do not add TLS or signature requirements by default.

Before handoff: `git diff --check`, inspect the diff, run the wrapper build, inspect the final JAR and record SHA-256.
Use `clean build` only for release preparation; never delete user/runtime directories. Build state, `.unimined/`, `.gradle/`,
logs, generated config and trust stores are ignored. Publishing requires explicit authorization.
