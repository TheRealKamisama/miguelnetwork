# CurseForge distribution plan for NeoForge 1.21.1

Status: implementation proposal, limited to the `main` branch.

## Scope and policy risk

This proposal applies only to the NeoForge 1.21.1 implementation on `main`.
Do not apply it to the Forge 1.20.1, Fabric, clean-room, or other migration
branches.

The intended release split is:

- GitHub Release: keep the current self-contained JAR with the pinned
  wstunnel 10.7.1 Windows x86-64 and Linux x86-64 binaries bundled.
- CurseForge: publish a separate JAR containing no native executable. After
  explicit configuration opt-in, this JAR downloads the pinned, platform-
  specific wstunnel archive at runtime and verifies it before execution.

This design reduces antivirus detections caused by an executable embedded in
the uploaded JAR. It does **not** guarantee CurseForge approval. CurseForge
support has explicitly said that the file would not be approved whether
wstunnel is bundled or downloaded at runtime, and described the transport
functionality itself as disallowed. Implementation and upload should therefore
be treated as conditional on a new written approval or an explicit maintainer
decision to accept the rejection risk. The project description must describe
the implementation honestly; it must not conceal runtime downloading or claim
that WebSocket itself provides DDoS protection.

## 1. Rewrite public-facing purpose descriptions

Position MiguelNetwork as a WebSocket transport adapter for server operators.
The supported use cases are:

- deployment behind WebSocket-capable reverse proxies and managed ingress;
- use on hosting platforms that expose HTTP/WebSocket but not arbitrary TCP;
- consolidating public entry points and keeping the Minecraft backend private;
- optional centralized TLS termination at an external gateway;
- use of routing, monitoring, rate limiting, origin protection, and DDoS
  mitigation supplied by the operator's chosen gateway or CDN.

Remove promotional claims about bypassing or mitigating ISP QoS, blocking,
firewalls, proxies, filtering, or network restrictions. Also remove wording
that says Minecraft traffic is disguised as ordinary web traffic. Do not
rewrite unrelated technical uses of `bypass`, such as ZstdNet compatibility
state or historical changelog entries.

Update at least:

- the short Chinese and English descriptions in `README.md`;
- the Chinese and English "primary use case" sections in `README.md`;
- feature bullets that currently emphasize a bundled sidecar;
- installation text so it distinguishes the GitHub and CurseForge artifacts;
- `mod_description` in `gradle.properties`;
- current-version user documentation that says no separate download is ever
  performed.

Keep historical changelog entries intact.

Suggested neutral short description:

> MiguelNetwork adds WebSocket transport support to Minecraft, enabling
> deployment behind WebSocket-capable reverse proxies, managed ingress, and
> hosting platforms that do not expose arbitrary TCP listeners. It preserves
> the Minecraft protocol while adapting the underlying transport.

Suggested statement about edge protection:

> Operators may use the routing, monitoring, rate-limiting, origin-protection,
> and DDoS-mitigation capabilities of their selected reverse proxy or CDN.
> MiguelNetwork does not provide DDoS mitigation by itself.

## 2. Add `CURSEFORGE.md`

Add `CURSEFORGE.md` at the repository root. English must appear first, followed
by a Chinese translation. Treat it as the source text for the CurseForge
project page and include it in the CurseForge-specific JAR.

The English section should state clearly:

1. The CurseForge artifact contains no wstunnel executable or other native
   executable.
2. WebSocket transport is disabled until the user explicitly enables the
   runtime download in the configuration file.
3. After opt-in, MiguelNetwork downloads only the pinned wstunnel 10.7.1 archive
   for the current operating system and architecture from the official
   `erebe/wstunnel` GitHub Release.
4. The archive and extracted executable are checked against pinned SHA-256
   values before installation or execution. A mismatch fails closed and the
   temporary file is removed.
5. The download is cached under
   `config/miguelnetwork/runtime/10.7.1/<platform>/`; it is not repeated when a
   valid cached executable is present.
6. Users may instead supply an existing executable with
   `-Dmiguelnetwork.wstunnel.path=<path>`.
7. The GitHub Release artifact is a different, self-contained distribution
   that still bundles the verified binaries.
8. MiguelNetwork enables deployment behind WebSocket-capable reverse proxies,
   managed ingress, and compatible CDNs. It does not itself provide a CDN or
   DDoS mitigation.

The Chinese section must convey the same facts, including that enabling the
download causes a network request to GitHub and execution of the verified
native program.

Suggested opt-in example:

```toml
[wstunnelDownload]
enable = true
```

The final key name must match the implemented NeoForge config exactly.

## 3. Create two artifacts from the same `main` tag

Keep the normal `jar`/`build` output as the GitHub artifact. It must retain the
current native resources and distribution verification.

Add a dedicated Gradle task, for example `curseForgeJar`, that:

- produces a distinctly named artifact such as
  `miguelnetwork-neoforge-1.21.1-<version>-curseforge.jar`;
- excludes every `native/**` entry and any other executable payload;
- includes `CURSEFORGE.md`, wstunnel provenance, pinned version, archive URLs,
  hashes, and required third-party notices;
- includes a small immutable distribution descriptor such as
  `META-INF/miguelnetwork/distribution.json` with a channel value of
  `curseforge` and `bundledWstunnel: false`;
- does not change the normal GitHub JAR's filename or contents.

Add `verifyCurseForgeDistribution` to open the completed archive and assert:

- neither `native/windows-x86_64/wstunnel.exe` nor
  `native/linux-x86_64/wstunnel` exists;
- no `.exe` entry exists anywhere in the CurseForge JAR;
- `CURSEFORGE.md`, the distribution descriptor, licenses, notices, and pinned
  download manifest are present;
- the descriptor says `curseforge` and `bundledWstunnel: false`.

Keep `verifyDistribution` for the GitHub artifact and continue checking that
both bundled executables have the expected hashes.

## 4. Require explicit opt-in before runtime download

Add a COMMON NeoForge configuration dedicated to external executable
acquisition. Do not overload the existing client and server `enabled` switches,
because an existing configuration could already contain `enabled = true` and
would not represent consent to a new download behavior.

Recommended file and key:

```text
config/miguelnetwork-common.toml
```

```toml
[wstunnelDownload]
enable = false
```

Required behavior:

- Default is `false` in every artifact.
- The setting is consulted only when the selected distribution contains no
  bundled executable and no valid `miguelnetwork.wstunnel.path` override.
- With `enable = false`, make no network request, create no temporary download,
  and start no native process. Log one clear instruction naming the exact file
  and key to change. MiguelNetwork transport must remain inactive.
- With `enable = true`, download automatically when either the client or
  dedicated server first needs wstunnel.
- The GitHub artifact continues using its bundled executable and must not
  download merely because this setting is true.
- A valid explicit path override remains highest priority and does not require
  downloading.

## 5. Implement a pinned, fail-closed downloader

Refactor `NativeWstunnel.resolve` into explicit resolution stages:

1. use and validate the operator-supplied path override;
2. use a cached executable only when its SHA-256 is correct;
3. extract the bundled executable when the distribution contains it;
4. otherwise, require runtime-download opt-in and download the pinned archive;
5. fail with an actionable error in every other case.

Downloader requirements:

- support only Windows x86-64 and Linux x86-64, matching the current product
  contract;
- use only HTTPS and the exact official release URL constructed from the
  checked-in manifest;
- download only the current platform archive;
- use connect and response timeouts and a descriptive User-Agent;
- write to a uniquely named temporary file in the target directory;
- enforce a reasonable maximum archive size;
- verify the archive SHA-256 before extraction;
- extract exactly one expected executable and reject path traversal, links,
  duplicate candidates, and unexpected entry types;
- verify the executable SHA-256 before atomic installation;
- serialize concurrent resolution so status pings cannot race downloads;
- remove temporary files on verification or extraction failure;
- never execute a file whose hash is unknown or mismatched;
- never silently fall back to an unadvertised target when acquisition fails.

Keep version, URLs, archive hashes, and binary hashes in one manifest-backed
source of truth. Add an explicit archive extraction dependency if needed rather
than relying on an undeclared transitive library.

## 6. Update the release workflow

Modify only the `main` NeoForge 1.21.1 release workflow:

1. Run the normal clean build and tests.
2. Locate, hash, attest, and publish the normal bundled JAR to GitHub Release.
3. Build and verify the separate CurseForge JAR.
4. Assert again in CI that the CurseForge JAR contains no native executable.
5. Upload only the `-curseforge.jar` artifact to CurseForge.
6. Preserve the exact CurseForge upload response for diagnostics.
7. Record separate SHA-256 values for both artifacts in release output.

Avoid globbing both artifacts into the existing "exactly one JAR" check. Use
explicit task outputs or exact filename patterns so the GitHub and CurseForge
artifacts cannot be swapped accidentally.

Do not modify workflows or build files on other branches as part of this work.

## 7. Tests and acceptance criteria

Add focused tests for:

- distribution descriptor parsing;
- opt-in disabled: zero network requests and a clear error/inactive result;
- valid cached executable: no download;
- explicit path override: no download;
- correct archive and executable hashes: successful atomic installation;
- wrong archive hash, wrong executable hash, malformed archive, duplicate
  executable, traversal entry, unsupported platform, timeout, and interrupted
  download: fail closed and leave no runnable unverified file;
- concurrent resolution: one completed download and one shared cached result;
- GitHub artifact resolution: bundled extraction remains unchanged and does not
  access the network;
- CurseForge artifact contents: no native executable;
- GitHub artifact contents: both pinned executables remain present and match
  their hashes.

Before hand-off, run:

```text
.\gradlew.bat test
.\gradlew.bat build --no-daemon
.\gradlew.bat curseForgeJar verifyCurseForgeDistribution --no-daemon
git diff --check
```

Record both artifact paths and SHA-256 values. Inspect both JAR listings and
confirm that only the GitHub artifact contains `native/**`.

## 8. Release gate

Before uploading the redesigned artifact, send CurseForge a narrow follow-up
request that discloses the runtime download behavior and asks whether this
specific design is acceptable. Their existing reply explicitly rejects runtime
downloading, so a successful antivirus scan alone must not be treated as policy
approval.

If CurseForge repeats the rejection, do not attempt to obscure, rename,
encrypt, or relocate the executable download. Keep the self-contained release
on GitHub and consider a pure Java/Netty transport redesign for a future
CurseForge submission.
