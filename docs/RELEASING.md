# Cleanroom release preparation

> Current release policy: [GitHub + CurseForge dual channels](RELEASE_CHANNELS.md). This replaces earlier single-channel or main-only instructions.

This branch is a migration, not a released compatibility promise. `mod_version` remains 0.2.0 pending a release decision.
The release workflow is manual preparation only: build, verify and upload a CI artifact. It does not publish a GitHub
release or upload to CurseForge. Do not reuse NeoForge game/loader IDs for Cleanroom.

Before publication, complete [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md), including real Java 25 Cleanroom client/server
validation, Windows/Linux native tests, and the production JAR hash. Publishing, tags, commits and pushes each require
the maintainer's explicit instruction.
