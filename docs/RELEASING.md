# Fabric release preparation

Use the checked-in wrapper with Java 21 to launch Gradle and a Java 17 target
toolchain. Run `./gradlew clean build --no-daemon`, then validate the production
`build/libs/miguelnetwork-fabric-1.20.1-<mod_version>.jar`.
Version metadata comes only from `gradle.properties`.

For offline native staging, pass `-PwstunnelArchiveDir=<archive-directory>`.
Both pinned wstunnel 10.7.1 archives and binaries must match their recorded
hashes. The remapped distribution also includes NightConfig core/toml 3.6.4
as nested JARs; ZstdNet itself is never bundled.

The migration branch's release workflow is manual verification and artifact
upload only. It does not respond to tags or publish to GitHub/CurseForge.
Before enabling publishing, finish the runtime matrix in FABRIC_VALIDATION.md,
choose a unique repository-wide tag/release scheme for multiple Loader assets,
and configure CurseForge metadata for Fabric / Minecraft 1.20.1 / Client / Server.
Do not reuse the inherited Forge publisher.

Creating a commit, tag, push, or public release requires explicit maintainer
authorization for that operation.