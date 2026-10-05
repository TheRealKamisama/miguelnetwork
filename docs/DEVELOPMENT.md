# Fabric development

This branch targets Minecraft 1.20.1, Fabric Loader 0.16.10, Fabric API
0.92.7+1.20.1, and Java 17 bytecode/runtime. Gradle 8.9 and Loom 1.7.4 are pinned.
Use Java 21 to launch Gradle and install a Java 17 toolchain for compilation/tests.
If automatic detection misses it, pass `-Porg.gradle.java.installations.paths=<jdk17-directory>`.

```text
.\gradlew.bat test
.\gradlew.bat build --no-daemon
.\gradlew.bat clean build --no-daemon
```

Use `./gradlew` on POSIX. `build` runs the JUnit suite and checks the production
`remapJar` output in `build/libs/miguelnetwork-fabric-1.20.1-0.2.0.jar`.
Do not distribute the named development JAR. Distribution checks inspect Fabric
metadata, intermediary ConnectionMixin mappings, Java bytecode, embedded
NightConfig dependencies, native hashes, and notices.

The default development runs have no ZstdNet. Put the matching Fabric JAR in
the isolated run's mods directory to exercise optional compatibility.
Only ZstdNet 1.4.7 and 1.4.8 are allowed, subject to exact API inspection before
the optional client Mixin is applied. See ZSTDNET_COMPATIBILITY.md for provenance.

Client entrypoints and Mixins are client-only. Server initialization must not
resolve Minecraft client classes. The gateway's event phase follows the default
SERVER_STARTED phase, where ZstdNet starts its listener.

Configuration is parsed through embedded NightConfig; no configuration Mod is
required. Fabric API is required. Existing TOML files are never rewritten.
Values are validated before publication; restart after editing settings.

Tests cover the inherited Discovery/transport/gateway behavior, configuration
parsing and default policy, and optional API signature checks. Real gameplay
and cross-platform coverage are recorded separately in FABRIC_VALIDATION.md.
The gateway retains the Java 17 branch's 128-connection admission limit and
two-direction worker capacity.

Before handoff run `git diff --check`, inspect the diff, and record the exact
production JAR path and SHA-256. Runtime data, logs, trust stores, dependency
JARs, and caches must remain outside Git.