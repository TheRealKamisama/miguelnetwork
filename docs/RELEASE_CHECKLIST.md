# Release checklist

This checklist prepares a local artifact for a future GitHub release. It does not
authorize a commit, tag, push, GitHub login, repository creation, or release
publication.

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

## Future publication (explicit approval required)

After the user confirms the remote, release notes, tag, and publication scope:

1. Make a focused release commit containing only reviewed changes.
2. Create and verify the requested version tag.
3. Push the branch/tag to the confirmed GitHub remote.
4. Create the GitHub Release and attach the JAR plus checksum and required notices.
5. Verify the published asset and workflow result, then report the URLs and checksums.

Do not place private keys or full runtime logs in a release archive. The license
audit is explicitly preliminary; complete the attribution/SBOM gate before calling
the release production-ready.
