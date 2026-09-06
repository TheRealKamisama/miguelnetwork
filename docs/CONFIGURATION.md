# Configuration

MiguelNetwork uses NeoForge TOML configuration. The dedicated-server listener is disabled by default. A client needs no
per-server entry: each server can publish a signed Discovery route.

## Dedicated server

Keep the Minecraft listener away from the public WebSocket listener and bind it to loopback or a trusted network:

```properties
server-ip=127.0.0.1
server-port=25566
```

After the first launch, edit `config/miguelnetwork-server.toml`:

```toml
enabled = true
transport = "WS"
bindHost = "0.0.0.0"
publicPort = 35548
pathPrefix = "miguelnetwork-v1"
certificate = ""
privateKey = ""
allowBuiltInSelfSigned = false

[discovery]
enabled = true
bindHost = "127.0.0.1"
port = 25567
advertisedTransport = "WSS"
advertisedHost = ""
advertisedPort = 0
configEpoch = 1
validitySeconds = 120
```

This example assumes Nginx terminates TLS and forwards private WS to port 35548. For direct TLS in wstunnel, select
`transport = "WSS"` and configure absolute `certificate` and `privateKey` PEM paths. On Windows, forward slashes in paths
avoid TOML escaping surprises.

The generated wstunnel restriction follows the actual `server-port`. Discovery signs that target port, so it no longer
has to be 25566. When supported ZstdNet is installed, its listener port is also restricted and advertised as a separate
higher-priority route.

The Discovery listener is plain internal HTTP and must not be exposed directly. If Nginx runs in another container or
machine, change `discovery.bindHost` to the Minecraft machine's protected LAN address and firewall the port so only the
proxy can reach it. Empty advertised host and zero advertised port derive the public endpoint from the forwarded Host
header. Increase `configEpoch` when intentionally replacing routes; clients reject lower values as a rollback.

The generated server identity at `config/miguelnetwork/generated/discovery-identity.key` is security-sensitive. Back it
up with the server and never publish it.

## Client

The generated `config/miguelnetwork-client.toml` works without editing:

```toml
enabled = true
pathPrefix = "miguelnetwork-v1"
maxTunnelProcesses = 8
legacyFallback = true
```

For each new logical Minecraft address, the client first requests
`https://<logical-server>/.well-known/miguelnetwork/v1`. It validates TLS, the Ed25519 signature, nonce, audience,
validity, pinned identity and configuration epoch, then starts the server-selected route. The player's server entry is
unchanged. Pins and the persistent WSS downgrade floor are stored in `config/miguelnetwork/client-trust.json`.

If Discovery is unavailable and `legacyFallback` is enabled, the alpha.2 probe remains available:

1. WSS with normal certificate and hostname verification;
2. plain WS;
3. unchanged vanilla Minecraft TCP when neither Upgrade probe succeeds.

Only this legacy path uses the fixed target-port convention 25566. A negative legacy TCP result is cached for 30
seconds; successful routes are cached for the game session. Least-recently-used sidecars are stopped at
`maxTunnelProcesses`.

Legacy WS fallback cannot be downgrade-proof on first contact. After WSS has succeeded, MiguelNetwork records a WSS
security floor and will no longer silently fall back to WS or TCP for that endpoint.

## Reverse proxy

Server-side `transport = "WS"` is suitable behind TLS-terminating Nginx. In that layout, `transport` describes the
private Nginx-to-wstunnel hop and `advertisedTransport = "WSS"` describes the public client hop. Preserve `$http_host`
so non-default public ports remain part of the signed audience.

See `DISCOVERY_PROTOCOL.md` for matching Nginx locations and the full protocol/security contract. Never expose a
production WS or internal Discovery listener directly to the internet.

## Isolated development only

Direct wstunnel WSS can use its built-in self-signed certificate only with `allowBuiltInSelfSigned = true`. Automated
local tests may pair this with `-Dmiguelnetwork.tls.verify=false`. Both settings are development-only and log warnings.

JVM property overrides used by automated validation remain documented in `TECHNICAL_VALIDATION.md`. Restart Minecraft
or the dedicated server after changing transport settings; live sidecar reconfiguration is not part of this Alpha.
