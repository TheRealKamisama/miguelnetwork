# Cleanroom user guide

This development branch targets Minecraft 1.12.2, Cleanroom 0.6.13-alpha and Java 25 on both client and dedicated server.
Install Cleanroom and its required compatibility components according to upstream instructions. Ordinary Forge/Java 8
and cross-Minecraft-version play are not supported. See [migration validation](CLEANROOM_MIGRATION.md) before deployment.

## Standalone setup

1. Install the remapped MiguelNetwork JAR into both `mods` directories.
2. Keep `server-port` as the private Minecraft backend port. `server-ip=127.0.0.1` is suitable for a local-only backend.
3. Start the server. MiguelNetwork creates `config/miguelnetwork-server.toml`, defaulting to `mode="STANDALONE"`,
   `bindHost="0.0.0.0"`, `publicPort=35548`, and Discovery enabled.
4. Forward/open that public TCP port. It must differ from the Minecraft port.
5. Add `hostname:35548` to the client's server list. Keep the normal domain name when using virtual hosting or WSS.

The same public port serves unsigned Discovery and plain WS. TLS certificates and a reverse proxy are optional.
For external-proxy WSS, follow [CONFIGURATION.md](CONFIGURATION.md); TLS is terminated by the external gateway.

## Discovery and fallback

The client requests Discovery v1, validates its content, selects an advertised compatible base route and starts a local
wstunnel process. There are no compression-filter adapters on this platform. Routes with unknown required filters are
rejected. An advertised unfiltered alternative may be selected; an unadvertised raw target may not.

When Discovery is unavailable, `legacyFallback=true` permits the existing WSS/WS/TCP probe. Set `legacyFallback=false`
to fail instead. Invalid Discovery/signatures or an incompatible manifest are not treated as permission to downgrade.
Signatures (`security.verifyDiscoverySignatures`) and WSS downgrade protection are optional and disabled by default.
When WSS is selected, normal certificate verification remains enabled unless explicitly changed by the operator.

## Diagnostic checklist

Server success must contain:

```text
standalone WS gateway is ready
Discovery and wstunnel share one port
```

Client Discovery success must contain `selected Discovery route`.
`Discovery unavailable`, `selected legacy ... route`, and `discovered vanilla TCP endpoint` identify fallback behavior.
`MiguelNetwork Discovery unavailable and legacyFallback is disabled for ...` identifies an explicitly blocked fallback.

The generated `config/miguelnetwork/generated/detected-server.toml` reports the effective Minecraft target.
Do not edit it. Change `server.properties` or `config/miguelnetwork-server.toml` and restart instead.
Client trust data is stored in `config/miguelnetwork/client-trust.json`; preserve it when optional pinning is used.

For startup errors, check distinct ports, the native platform/permissions, effective TOML values and sidecar output.
Keep generated config, credentials, keys and logs out of Git. Share only redacted diagnostic excerpts.
