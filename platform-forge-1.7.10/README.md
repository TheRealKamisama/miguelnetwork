# MiguelNetwork — Forge 1.7.10 / Java 25

This independent platform build preserves the root NeoForge 1.21.1 project.
The root `gradle.properties` remains the only source of the mod version.

Use the checked-in wrapper from this directory with an Azul Java 25 JDK:

```powershell
.\gradlew.bat test
.\gradlew.bat build --no-daemon
```

On POSIX use `./gradlew`. `build` produces the reobfuscated production JAR and
the no-native distribution. `jar` alone creates an intermediate development JAR;
use `reobfJar` for production. Do not install `*-dev*` files in a normal instance.

- [Installation and configuration](../docs/FORGE_1_7_10_JAVA_25_USER_GUIDE.md)
- [Validation evidence and scope](../docs/FORGE_1_7_10_JAVA_25_VALIDATION.md)
- [No-native acquisition disclosure](NO_NATIVE.md)
- [Native ZstdNet protocol and lifecycle](../docs/NATIVE_INTEROP_V1.md)

The runtime acceptance driver under `src/runtimeProbe` is a separate test mod.
It is never included in the MiguelNetwork distributions. Do not install that
driver into ordinary player instances: it automatically connects, disconnects,
and exits the client for testing.
