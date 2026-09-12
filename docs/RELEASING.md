# Build and release

MiguelNetwork builds with Java 21. The standard build downloads the pinned official wstunnel archives, validates the
archive and extracted executable SHA-256 values from
`src/main/resources/META-INF/miguelnetwork/wstunnel-manifest.json`, and packages Windows x64 and Linux x64 executables
into the Mod JAR.

## Local build

```text
./gradlew build
```

For an offline or pre-downloaded build, place both archives named by the manifest in one directory:

```text
./gradlew build -PwstunnelArchiveDir=/absolute/path/to/archives
```

`verifyDistribution`, which is part of `check` and therefore `build`, reopens the output JAR and verifies both native
binary hashes plus the required MiguelNetwork and wstunnel legal/provenance files. The release filename is
`miguelnetwork-neoforge-1.21.1-<version>.jar`.

## GitHub repository configuration

Create a GitHub Actions environment named `curseforge`. Configure it with:

- environment secret `CURSEFORGE_API_TOKEN`: a CurseForge upload API token;
- environment variable `CURSEFORGE_PROJECT_ID`: the numeric MiguelNetwork CurseForge project ID.

The workflow queries CurseForge's authenticated version and version-type catalogs before building. It resolves the
numeric IDs for Minecraft 1.21.1 within the `minecraft-1-21` type, NeoForge within the Modloader type, and both Client
and Server within the Environment type. This avoids storing unstable numeric IDs in repository settings, disambiguates
duplicate display names, and fails early if the token is invalid or any required entry cannot be resolved exactly.

Environment protection rules are recommended so that a maintainer approves each public upload. Do not put an API token
in `gradle.properties`, workflow YAML, commits, tags, or release archives.

The token can be entered without placing it on the command line or in shell history:

```text
gh secret set CURSEFORGE_API_TOKEN --env curseforge
```

Set the non-secret project ID from the CurseForge project page:

```text
gh variable set CURSEFORGE_PROJECT_ID --env curseforge --body <numeric-project-id>
```

## Release procedure

1. Complete the third-party release gate in `THIRD_PARTY_LICENSE_AUDIT.md` for the pinned wstunnel build.
2. Move the current `Unreleased` changelog entries under a heading matching the version in `gradle.properties`.
3. Run `./gradlew clean build` and test the output on the supported client and dedicated-server platforms.
4. Create and push an annotated tag exactly matching `v<mod_version>`, for example `v0.2.0`.
5. Approve the `curseforge` GitHub environment deployment if protection is enabled.

The tag workflow rejects a version mismatch, performs a clean verified build, creates `SHA256SUMS`, generates a GitHub
artifact provenance attestation, publishes or updates the GitHub Release assets, and uploads the same JAR to CurseForge.
Pre-release labels containing `alpha` or `beta` map to the corresponding CurseForge release type.

If a run fails after CurseForge accepts the file, inspect the CurseForge project before rerunning the job so that a
duplicate file is not created.
