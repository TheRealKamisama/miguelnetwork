# Agent contribution guide

## Project contract

- This branch ports MiguelNetwork to Java 25 / Cleanroom 0.6.13-alpha / Minecraft 1.12.2. It bundles the pinned
  `wstunnel` 10.7.1 binaries for Windows x86-64 and Linux x86-64.
- `STANDALONE` is the default server mode: the built-in gateway serves Discovery and
  forwards WebSocket traffic on `server.publicPort`. `EXTERNAL_PROXY` is for Nginx or
  another gateway that owns the public endpoint.
- Discovery v1 is the normal client control path. The client may use its legacy
  WSS/WS/TCP probe only when Discovery is unavailable and `legacyFallback` is enabled.
  The optional reflective ZstdNet Cleanroom adapter supports only the verified
  `1.4.7-cleanroom-alpha.1` API; unknown required filters remain unsupported.
- Use the pinned Unimined 1.4.36-kappa and Gradle 9.7.0 wrapper. Build and run on Java 25.
  MCP's legacy merge tool additionally requires a JDK 8 build helper; see docs/DEVELOPMENT.md.
  Do not declare ordinary Forge/Java 8 support. Preserve unknown-required-filter rejection.

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
.\gradlew.bat clean build         # release-preparation build (remapped production JAR)
```

On a POSIX shell, use `./gradlew` instead. If dependency resolution fails, record
the first network/repository error and retry only after checking Java 25,
`gradle/wrapper/gradle-wrapper.properties`, and the local Gradle cache. Do not
replace the wrapper with an unpinned system Gradle.

Before hand-off, run `git diff --check`, inspect `git diff`, and record the exact
artifact path and SHA-256. A successful build produces
`build/libs/miguelnetwork-cleanroom-1.12.2-<mod_version>.jar`.
The `dev` and `shadow-dev` JARs are not distributable; verify `remapShadowJar` output.

## Code and documentation rules

- The source of truth for the mod version is `mod_version` in `gradle.properties`.
  When bumping it, update current-version references in README/docs/CHANGELOG and
  regenerate metadata through a build. Keep historical changelog entries intact.
- Keep Discovery and transport behavior fail-closed: required unknown filters or
  invalid manifests must not silently connect to an
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

Agents may create commits and push branch changes autonomously within the user's
task scope; separate confirmation is not required for commits or branch pushes.
Review the diff, preserve unrelated user changes, and verify the destination remote
before pushing. This permission does not authorize force-pushing or rewriting
shared history.

Publishing a release or artifact, creating or pushing release tags, and triggering
a release workflow still require explicit user authorization for that operation.
Do not log in to GitHub or create a repository without explicit authorization.
Local release preparation may install and verify GitHub CLI, build artifacts, and
documentation. Follow `docs/RELEASE_CHECKLIST.md` for release gates.

## Distribution policy

All maintained branches use GitHub Release (bundled natives) and CurseForge (native-free production JAR with the main runtime acquisition policy). See `docs/RELEASE_CHANNELS.md`. Run both distribution verifiers. Keep the public `CURSEFORGE.md` free of acquisition settings/details and aligned with STANDALONE/plain WS usage.
