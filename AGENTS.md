# Agent contribution guide

## Project contract

- MiguelNetwork is a Java 21 NeoForge mod for Minecraft 1.21.1. It bundles the pinned
  `wstunnel` 10.7.1 binaries for Windows x86-64 and Linux x86-64.
- `STANDALONE` is the default server mode: the built-in gateway serves Discovery and
  forwards WebSocket traffic on `server.publicPort`. `EXTERNAL_PROXY` is for Nginx or
  another gateway that owns the public endpoint.
- Discovery v1 is the normal client control path. The client may use its legacy
  WSS/WS/TCP probe only when Discovery is unavailable and `legacyFallback` is enabled.
  ZstdNet compatibility is intentionally version-gated to 1.4.7 and 1.4.8.
- MiguelNetwork itself is compiled against NeoForge 21.1.77. The verified ZstdNet
  1.4.7/1.4.8 JAR metadata requires NeoForge 21.1.221 or newer, so use that higher
  floor whenever ZstdNet is installed.

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
.\gradlew.bat clean test jar      # release-preparation build
```

On a POSIX shell, use `./gradlew` instead. If dependency resolution fails, record
the first network/repository error and retry only after checking Java 21,
`gradle/wrapper/gradle-wrapper.properties`, and the local Gradle cache. Do not
replace the wrapper with an unpinned system Gradle.

Before hand-off, run `git diff --check`, inspect `git diff`, and record the exact
artifact path and SHA-256. A successful build produces
`build/libs/miguelnetwork-<mod_version>.jar`.

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
