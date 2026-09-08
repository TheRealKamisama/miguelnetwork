# User guide

MiguelNetwork carries Minecraft Java Edition TCP traffic through the bundled
`wstunnel` sidecar. Install the same MiguelNetwork version on the dedicated server
and on each client. The current target is Minecraft 1.21.1 and Java 21;
MiguelNetwork is compiled against NeoForge 21.1.77. Optional ZstdNet 1.4.7/1.4.8
requires NeoForge 21.1.221 or newer according to its JAR metadata.

## Quick start: standalone server

Standalone is the default and needs one externally reachable TCP port.

1. Install NeoForge, MiguelNetwork, and (optionally) a supported ZstdNet on the
   server and client.
2. Set the Minecraft `server-ip`/`server-port` in `server.properties`. Keep the
   Minecraft and ZstdNet listener ports private.
3. Leave `config/miguelnetwork-server.toml` at `mode = "STANDALONE"`, choose an
   unused `publicPort`, and expose only that port in the firewall/NAT.
4. Start the server and add `public-host:publicPort` to the client's Minecraft
   server list. The client discovers the transport and backend target; no target
   port or WS/WSS selection is needed.

The complete server and Nginx configuration examples are in
[`CONFIGURATION.md`](CONFIGURATION.md). Standalone deliberately advertises plain
WS and does not create certificates. Use `EXTERNAL_PROXY` when TLS/WSS must be
terminated by Nginx or another gateway.

## Client behavior and fallback

The normal client sequence is:

1. POST a nonce-bearing request to `/.well-known/miguelnetwork/v1` over HTTPS,
   then HTTP if HTTPS cannot be reached.
2. Validate the protocol, audience, nonce, and validity window; optionally verify
   the signed envelope and pinned server identity.
3. Select the highest-priority compatible route (the ZstdNet route first when the
   client has a supported ZstdNet installation) and start a local wstunnel proxy.
4. Redirect Minecraft's socket to that local proxy while preserving the logical
   server address for the Minecraft handshake.

If Discovery is unavailable, `legacyFallback = true` (the compatibility default)
allows the older WSS → WS → TCP probe. This is not used when a valid Discovery
manifest has been received. To require Discovery-only operation, set this in the
client TOML:

```toml
legacyFallback = false
```

The equivalent development override is
`-Dmiguelnetwork.client.legacyFallback=false`.

Unsigned Discovery is still Discovery: `signResponses = false` and
`verifyDiscoverySignatures = false` control authentication, not route selection.
Normal WSS certificate verification remains enabled unless it is explicitly
disabled for an isolated development run.

## Log checklist

Inspect `logs/latest.log` on both sides after a fresh restart. A healthy standalone
server includes lines equivalent to:

```text
MiguelNetwork standalone WS gateway is ready on <bind>:<publicPort>; Discovery and wstunnel share one port
MiguelNetwork detected Minecraft target <host>:<server-port> from server.properties/runtime
MiguelNetwork Discovery signatures are disabled
```

On a client that used Discovery, look for:

```text
MiguelNetwork selected Discovery route <route-id> for <host>:<port>
MiguelNetwork redirects <host>:<port> through route discovery:<...> to 127.0.0.1:<local-port>
```

For ZstdNet, the supported-version adapter logs its detected version and a
`composed ZstdNet` route. The following messages are evidence of fallback or a
Discovery problem and should not appear in a Discovery-only deployment:

- `MiguelNetwork Discovery unavailable ...`
- `MiguelNetwork selected legacy ... route ...`
- `MiguelNetwork discovered vanilla TCP endpoint ...`

`discovered vanilla TCP endpoint` is especially important: it means the connection
was intentionally left on the vanilla TCP path. A WS warning is expected in
standalone mode because that mode is unencrypted by design; it is not itself a
fallback indicator.

## Generated files and common problems

- `config/miguelnetwork/generated/detected-server.toml` records the resolved
  Minecraft/ZstdNet target. It is diagnostic and must not be hand-edited.
- `config/miguelnetwork/generated/restrictions.yaml` is the server-side wstunnel
  allowlist. It is regenerated at server start.
- `config/miguelnetwork/client-trust.json` stores client Discovery pins only when
  signed verification or WSS downgrade protection is enabled.
- A public-port bind failure normally means `publicPort` is already in use or is
  equal to `server-port`/the detected ZstdNet port. Pick a distinct port and
  restart.
- An empty Discovery response or a route mismatch usually means the public host
  and port are not preserved by the reverse proxy. Preserve the `Host` header (and
  `X-Forwarded-Host` for the Discovery upstream); see `CONFIGURATION.md`.
- Keep the Minecraft/ZstdNet backend private. Exposing those listeners bypasses
  the intended wstunnel restriction and route selection.

For protocol details, see [`DISCOVERY_PROTOCOL.md`](DISCOVERY_PROTOCOL.md); for
ZstdNet API/version policy, see [`ZSTDNET_COMPATIBILITY.md`](ZSTDNET_COMPATIBILITY.md).
