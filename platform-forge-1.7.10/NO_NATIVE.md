# No-native Forge 1.7.10 distribution

This JAR includes no wstunnel executable. It still requires Java 25 and the pinned
LWJGL3ify/UniMixins runtime described in `docs/FORGE_1_7_10_JAVA_25_USER_GUIDE.md`.

Choose one acquisition method:

- Supply the exact upstream wstunnel 10.7.1 executable with
  `-Dmiguelnetwork.wstunnel.path=<path>`. Its SHA-256 must match the bundled manifest.
- Explicitly opt into downloading the pinned upstream archive by setting
  `wstunnelDownload.enable=true` in `config/miguelnetwork-common.properties`.
  Both the archive and extracted binary are verified against the pinned SHA-256.

Download is disabled by default. Missing, modified, or unsupported binaries leave
the transport inactive. Supported native platforms are Windows x86-64 and Linux
x86-64. The acquisition descriptor retains the existing `curseforge` channel
identifier for compatibility; producing this file does not publish it anywhere.

wstunnel retains its BSD-3-Clause license. See `THIRD_PARTY_NOTICES.md` and
`META-INF/licenses/wstunnel-BSD-3-Clause.txt` inside the JAR.
