# Agent contribution guide

## Project contract

- This branch is a Java 17 Forge mod for Minecraft 1.20.1. It bundles the pinned
  `wstunnel` 10.7.1 binaries for Windows x86-64 and Linux x86-64.
- `STANDALONE` is the default server mode: the built-in gateway serves Discovery and
  forwards WebSocket traffic on `server.publicPort`. `EXTERNAL_PROXY` is for Nginx or
  another gateway that owns the public endpoint.
- Discovery v1 is the normal client control path. The client may use its legacy
  WSS/WS/TCP probe only when Discovery is unavailable and `legacyFallback` is enabled.
  ZstdNet compatibility is intentionally version-gated to 1.4.7 and 1.4.8.
- MiguelNetwork itself is compiled against Forge 47.1.3. The pinned optional
  development dependency is ZstdNet 1.4.8 for Forge 1.20.1 (CurseForge file 8752125).
  Its reflective API has been checked; the inherited 1.4.7 gate is not evidence
  that a Forge 1.4.7 build has been verified. Never install a NeoForge JAR here.

## Transport and encryption principle

- MiguelNetwork does not require transport encryption. Plain WebSocket is a
  supported first-class transport, not a development-only fallback.
- `STANDALONE` is the primary and recommended deployment. Its built-in gateway
  deliberately multiplexes Discovery and unencrypted WS on one public port, with
  no certificate setup or external proxy required.
- `EXTERNAL_PROXY` with WSS is the secondary supported deployment for operators
  who choose TLS. TLS certificates and WSS termination belong to Nginx, Caddy,
  HAProxy, or another external gateway; the private hop to MiguelNetwork remains
  plain WS. Do not add certificate management back to the Mod.
- Do not describe WSS, TLS, signed Discovery, or downgrade enforcement as the
  production default or as mandatory security requirements. These are optional
  operator-selected controls and are disabled by default. Do not make WS or
  unsigned Discovery fail merely because encryption/authentication is absent.
- Any future proposal that changes this hierarchy or makes encryption mandatory
  is a product-policy change, not a routine hardening task, and requires explicit
  maintainer direction before implementation.

## Before changing files

1. Run `git status --short --branch` and inspect the diff. Existing edits and
   untracked files belong to the user unless the task explicitly says otherwise.
2. Do not use `git reset`, `git checkout`, `git clean`, recursive deletion, or broad
   rewrites to make the tree convenient. Never overwrite runtime logs, generated
   config, trust stores, or user-provided server files.
3. Build output (`build/`), Gradle state (`.gradle/`), development runs (`run/`),
   and `*.log` are ignored. Keep generated/runtime data out of Git; extend
   `.gitignore` when a new cache is discovered.

## Build and verification

Use the checked-in Gradle wrapper so the Gradle version is reproducible:

```text
.\gradlew.bat test                 # unit tests
.\gradlew.bat build --no-daemon   # tests plus the distributable JAR
.\gradlew.bat clean build         # tests, reobfuscation, and distribution checks
```

On a POSIX shell, use `./gradlew` instead. If dependency resolution fails, record
the first network/repository error and retry only after checking Java 17,
`gradle/wrapper/gradle-wrapper.properties`, and the local Gradle cache. Do not
replace the wrapper with an unpinned system Gradle.

Before hand-off, run `git diff --check`, inspect `git diff`, and record the exact
artifact path and SHA-256. A successful build produces
`build/libs/miguelnetwork-forge-1.20.1-<mod_version>.jar`. The `build/devlibs/`
JAR is not a distribution. `verifyDistribution` checks the reobfuscated JAR's
Forge metadata, Mixin manifest/refmap, Java 17 bytecode, natives, and notices.

## Code and documentation rules

- The source of truth for the mod version is `mod_version` in `gradle.properties`.
  When bumping it, update current-version references in README/docs/CHANGELOG and
  regenerate metadata through a build. Keep historical changelog entries intact.
- Keep Discovery and transport behavior fail-closed: required unknown filters,
  invalid manifests, or unsupported ZstdNet APIs must not silently connect to an
  unadvertised target.
- Keep docs aligned with `ServerConfig`, `ClientConfig`, and the actual logger
  messages. Use the exact config key and log text when documenting diagnostics.
- Do not add credentials, private keys, server logs, local paths, or generated
  `config/miguelnetwork/generated/` files to the repository.

## Runtime diagnosis

For a standalone server, confirm the log contains `standalone WS gateway is ready`
and `Discovery and wstunnel share one port`. On a client using Discovery, expect
`selected Discovery route`; `Discovery unavailable`, `selected legacy ... route`,
or `discovered vanilla TCP endpoint` indicates that the fallback path was used or
entered. Set `legacyFallback = false` when a deployment must fail rather than
downgrade. See `docs/USER_GUIDE.md` for the complete checklist.

## Release safety

Local release preparation may install and verify GitHub CLI, build artifacts, and
documentation. Do not log in to GitHub, create a repository/release, tag, commit,
push, or publish an artifact unless the user explicitly requests that exact
operation. The current remote may be a local mirror; verify it before any future
GitHub operation. Follow `docs/RELEASE_CHECKLIST.md` for the remaining gate.

## Commit attribution

- When the user authorizes a commit, preserve the user as its primary author:
  `TheRealKamisama <TheRealKamisama@163.com>`, unless the user explicitly supplies
  a different author identity. Do not replace the author with an agent identity.
- Every Codex-assisted commit must include this exact trailer, separated from
  the message body by a blank line:
  `Co-authored-by: Codex <codex@openai.com>`.
- Verify the author and trailer with `git show --format=full` after committing.
  This attribution rule does not itself authorize commits, pushes, or releases.
