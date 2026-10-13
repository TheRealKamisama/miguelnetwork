# Fabric release checklist

> Current release policy: [GitHub + CurseForge dual channels](RELEASE_CHANNELS.md). This replaces earlier single-channel or main-only instructions.

- [ ] Build with the checked-in wrapper; verify tests and remapJar distribution checks.
- [ ] Check exact Minecraft version, Fabric Loader/API dependency floors, Java
      bytecode, client-only Mixins, intermediary refmap, and absence of Forge metadata.
- [ ] Check both pinned native hashes and all licenses, notices, nested libraries,
      and NightConfig source archives.
- [ ] Record the production JAR path, SHA-256, target OS/JVM, and test results.
- [ ] Complete actual gameplay tests: server-list join, direct connect, status,
      cancel, reconnect, and shutdown with/without each supported ZstdNet version.
- [ ] Complete Linux x86-64 and optional WSS/signature deployment validation.
- [ ] Verify default unsigned STANDALONE WS and required-filter failure behavior.
- [ ] Review version and current documentation; keep historical changelogs intact.
- [ ] Define unique tags/assets and correct Fabric/MC/Client/Server publishing metadata.

The migration branch only has build and manual release-preparation workflows.
No workflow currently publishes Fabric artifacts. A public release must not be
inferred from an uploaded CI build. Commit, tag, push, and publication each
require the maintainer's explicit instruction. See RELEASING.md.