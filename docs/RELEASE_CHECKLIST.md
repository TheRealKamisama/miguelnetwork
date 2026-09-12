# Release checklist

This checklist covers local release preparation and the tag-driven GitHub Actions
publication flow. It does not itself authorize a commit, tag, push, GitHub login,
or release publication.

## Repository and version gate

- [ ] Read `AGENTS.md` and run `git status --short --branch`.
- [ ] Review all existing staged, unstaged, and untracked user changes; do not
      discard or include them accidentally.
- [ ] Confirm `mod_version` in `gradle.properties` is the intended release version.
- [ ] Confirm current-version references in README, configuration, Discovery,
      compatibility, and CHANGELOG docs; retain historical version entries.
- [ ] Confirm no generated runtime files, private keys, credentials, logs, or local
      paths are tracked.

## Build and artifact gate

Run the wrapper from the repository root:

```text
.\gradlew.bat clean test jar
git diff --check
```

- [ ] All tests pass and the JAR exists under `build/libs/`.
- [ ] Inspect the JAR for `META-INF/neoforge.mods.toml`, the native binaries,
      `wstunnel-manifest.json`, notices, and licenses.
- [ ] Verify the embedded metadata reports the intended version.
- [ ] Record a SHA-256 checksum (PowerShell: `Get-FileHash <jar> -Algorithm SHA256`).
- [ ] Recheck the bundled wstunnel checksums and `docs/THIRD_PARTY_LICENSE_AUDIT.md`
      if native binaries or their version changed.

## GitHub CLI gate

Install GitHub CLI through the official Windows package source if it is missing:

```text
winget install --id GitHub.cli --exact --source winget
gh --version
```

The CLI may be installed and version-checked without logging in. Before any future
remote operation, verify the executable and remote explicitly:

```text
gh --version
git remote -v
gh auth status
```

`gh auth status` is read-only; `gh auth login` requires a deliberate user request.
The current checkout's `source` remote may be a local mirror rather than GitHub.
Do not infer a GitHub repository from the project name.

## CurseForge automation gate

- [ ] Confirm that the GitHub Actions environment is named exactly `curseforge`.
- [ ] Configure the environment secret `CURSEFORGE_API_TOKEN` through GitHub's UI
      or `gh secret set`; never paste it into an issue, chat, workflow, or commit.
- [ ] Set environment variable `CURSEFORGE_PROJECT_ID` to the numeric MiguelNetwork
      CurseForge project ID.
- [ ] Confirm the workflow can use the token and version-type catalog to resolve
      exactly one Minecraft 1.21.1 entry, one NeoForge entry, and the Client and
      Server environment entries.
- [ ] Review any environment protection rules and required reviewers.

See `docs/RELEASING.md` for the secure `gh` commands and automated workflow details.

## Tag publication (explicit approval required)

After the user confirms the remote, release notes, tag, and publication scope:

1. Make a focused release commit containing only reviewed changes.
2. Create an annotated tag named exactly `v<mod_version>` and verify it.
3. Push the branch and tag to the confirmed GitHub remote. Pushing the tag starts
   `.github/workflows/release.yml`.
4. Approve the `curseforge` environment deployment if protection rules require it.
5. Verify that the workflow built and checked the JAR, published the JAR and
   `SHA256SUMS` to the GitHub Release, created the provenance attestation, and
   uploaded the same JAR to CurseForge.
6. Record the GitHub Release URL, CurseForge file URL, and SHA-256.

Do not manually upload the JAR again after the workflow succeeds. If a retry is
needed after the CurseForge upload step may have completed, inspect the project
files first to avoid publishing a duplicate.

Do not place private keys or full runtime logs in a release archive. The license
audit is explicitly preliminary; complete the attribution/SBOM gate before calling
the release production-ready.
