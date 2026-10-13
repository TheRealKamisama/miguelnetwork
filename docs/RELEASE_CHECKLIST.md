# Cleanroom release gates

> Current release policy: [GitHub + CurseForge dual channels](RELEASE_CHANNELS.md). This replaces earlier single-channel or main-only instructions.

- Pin Minecraft 1.12.2, the tested Cleanroom version, Java 25, wrapper, mappings and Unimined.
- Run the checked-in wrapper `clean build`; tests and production distribution checks must pass.
- Check the remapped JAR's metadata, Java bytecode, Mixin registration/mapping, relocated configuration library,
  both wstunnel 10.7.1 hashes, and license/notice files. Exclude development JARs.
- Complete the existing [wstunnel attribution audit](THIRD_PARTY_LICENSE_AUDIT.md) release gate before publication;
  native hash verification does not finish the transitive license/SBOM audit.
- On real Cleanroom client/server installations, verify server-list status, login, cancel/reconnect, original handshake
  domain/port and Forge marker, and dedicated-server client-class isolation.
- When ZstdNet Cleanroom `1.4.7-cleanroom-alpha.1` is installed, verify manual and `auto_takeover` listener/backend
  discovery, the advertised `zstdnet-stream` route, compressed login/status traffic, loopback recursion guard, reconnect
  and fallback to `minecraft-primary` without it.
- Verify default unsigned STANDALONE WS/Discovery, optional external-proxy WSS, invalid manifests, unknown required filters,
  and Discovery failure with legacyFallback enabled and disabled.
- Cover Windows and Linux x86-64, concurrent duplex traffic, half-close, network interruption, shutdown and process cleanup.
- Record the exact production artifact path, SHA-256, selected Loader/Java/Mod versions and any skipped tests.
- Update current release version references from gradle.properties; preserve historical changelog entries.
- Select valid distribution-platform Minecraft/Loader categories; never label this JAR NeoForge or ordinary Forge/Java 8.
- Obtain explicit instructions before commit, tag, push or publication. Verify the remote first.
