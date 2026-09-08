# Development guide

## Prerequisites

- Java 21 (the Gradle toolchain and NeoForge target are both Java 21).
- A checkout with the included Gradle wrapper. No system Gradle installation is
  required.
- Network access on the first build so Gradle can resolve NeoForge and test
  dependencies. The wrapper and dependency caches are intentionally not tracked.

The project targets Minecraft 1.21.1 and compiles against NeoForge 21.1.77. The
verified ZstdNet 1.4.7/1.4.8 JAR metadata requires NeoForge 21.1.221 or newer, so
integration runs with ZstdNet must use that higher floor. The bundled wstunnel
sidecar is version 10.7.1; its platform binaries and SHA-256 manifest are part of
the source tree and should be changed only with a corresponding license/hash audit.

## Reproducible local commands

From the repository root:

```text
.\gradlew.bat --version
.\gradlew.bat test
.\gradlew.bat build --no-daemon
.\gradlew.bat clean test jar
```

Use `./gradlew` on Linux/macOS. `build` runs the JUnit suite and produces the Mod
JAR under `build/libs/`. The `clean test jar` command is useful before a release,
but removes only ignored build output; it does not touch source or runtime data.

Useful local checks:

```text
git diff --check
git status --short --branch
jar tf build/libs/miguelnetwork-0.2.0.jar | Select-String 'neoforge.mods.toml|wstunnel-manifest|native/'
```

The final `jar` inspection is illustrative for PowerShell; on POSIX systems use
`jar tf ... | grep -E 'neoforge.mods.toml|wstunnel-manifest|native/'`.

## Test coverage map

- `core/`: command construction, endpoint matching, native-binary extraction,
  and process lifecycle.
- `discovery/`: request/manifest codecs, route ordering, nonce/audience/validity,
  and Ed25519 identity handling.
- `client/`: Discovery/transport probing, trust-store behavior, and tunnel
  lifecycle.
- `server/`: runtime endpoint resolution, restriction generation, and the
  standalone gateway's Discovery/Upgrade split over loopback sockets.
- `compat/`: the exact ZstdNet version allowlist. Keep tests for both 1.4.7 and
  1.4.8 and for nearby unsupported versions whenever that gate changes.

## Runtime validation

The automated suite does not prove a remote deployment is healthy. For a local
standalone run, use the properties documented in
[`TECHNICAL_VALIDATION.md`](TECHNICAL_VALIDATION.md), then inspect the server and
client `logs/latest.log` files. The server must report the standalone gateway and
the client must report a selected Discovery route. A `selected legacy` or
`discovered vanilla TCP` line means the fallback path was entered; do not call that
run a Discovery-only validation.

When diagnosing an installed deployment, collect the relevant log excerpt,
`config/miguelnetwork/generated/detected-server.toml`, the effective TOML settings,
and the exact Mod JAR checksum. Redact hostnames, credentials, private keys, and
unrelated player data before sharing them.

## Change and version workflow

1. Read `AGENTS.md`, check the worktree, and keep unrelated user changes intact.
2. Make the smallest source/doc change that satisfies the issue.
3. Update tests and docs when behavior or config changes. Keep historical changelog
   sections unchanged.
4. Bump `mod_version` in `gradle.properties` only when the task calls for a release;
   update the current version in README and protocol/compatibility docs.
5. Run tests, `build --no-daemon`, `git diff --check`, and inspect the generated
   artifact before handing off.

Never commit generated `build/`, `.gradle/`, `run/`, `*.log`, server config, trust
stores, or Discovery private keys. Do not commit, tag, push, log in to GitHub, or
create a release unless the user explicitly authorizes that operation.
