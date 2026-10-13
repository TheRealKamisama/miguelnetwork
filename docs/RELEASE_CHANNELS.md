# Dual-channel releases / 双频道发布

All maintained branches use GitHub Release and CurseForge. This policy supersedes earlier main-only release planning.

- GitHub Release receives the production JAR with pinned, hash-verified Windows/Linux wstunnel 10.7.1 binaries.
- CurseForge receives only `*-curseforge.jar`, with no `native/**`, `.exe`, `.dll` or `.so` entries. It preserves production mappings, manifests, dependencies and notices.
- The maintainer reported on 2026-10-13 that the current main CurseForge edition passed review. This is user-reported acceptance, not independent approval of future files or other platform builds.
- Runtime acquisition follows main: default off, explicit operator opt-in, official pinned archive, archive and executable SHA-256 checks, bounded safe extraction, atomic installation and serialized cache reuse. Invalid inputs leave transport inactive.
- `CURSEFORGE.md` is the public bilingual usage guide. Keep it aligned with platform usage and STANDALONE/plain WS. Do not put runtime download settings or acquisition details in that document; retain technical configuration in the operator/developer documentation.

## Build and release

Run the checked-in wrapper in `.`: `./gradlew build curseForgeJar verifyCurseForgeDistribution --no-daemon`. `check` includes both distribution verifiers. Never publish development, sources or shadow-dev JARs.

This branch uses release tags `native-interop-cleanroom-1.12.2-v<mod_version>` (for example `native-interop-cleanroom-1.12.2-v0.2.1`). Tags are unique across platforms/branches. The workflow validates the version, builds and verifies both channels, calculates SHA-256, uploads the bundled JAR to GitHub Release and the native-free JAR to CurseForge. Configure the `curseforge` environment with `CURSEFORGE_API_TOKEN` and `CURSEFORGE_PROJECT_ID`; catalog lookup must resolve the exact Minecraft 1.12.2, Forge, Client and Server IDs, otherwise publication fails. No unrelated loader substitution is allowed.

The current task configures release automation; a release still requires an explicitly authorized tag/publication. A release that uploads successfully still has a separate CurseForge moderation state.

## Operator acquisition settings

Set `[wstunnelDownload] enable = true` in `config/miguelnetwork-common.toml`.
Alternatively supply the exact pinned executable with `-Dmiguelnetwork.wstunnel.path=<path>`. The system property `-Dmiguelnetwork.wstunnel.download.enabled=true` overrides the download setting. A verified cache or bundled binary needs no network request. Corrupt downloads, unsupported platforms and missing authorization do not start a sidecar.

所有维护分支统一采用 GitHub Release（内置二进制）与 CurseForge（不含二进制）双频道。运行时下载保持默认关闭、显式启用、固定版本及双重哈希校验。公开的 `CURSEFORGE.md` 仅保留双语使用说明，技术获取配置放在本文件。维护者已报告 main 特供版通过审核；其他版本仍须按平台验证并接受各自审核。

CurseForge uses the Forge catalog family for this Cleanroom build. The file display name, Mod metadata and user guide explicitly require Cleanroom 0.6.13-alpha / Java 25; the catalog family does not declare ordinary Forge/Java 8 compatibility.
