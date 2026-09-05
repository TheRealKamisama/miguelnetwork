# Configuration

MiguelNetwork uses NeoForge TOML configuration. The dedicated-server listener is disabled by default. The client uses
automatic transport discovery by default and does not need a per-server entry.

## Dedicated server

First move the internal Minecraft listener away from the WebSocket listener port in `server.properties`:

```properties
server-ip=127.0.0.1
server-port=25566
```

After the first server launch, edit `config/miguelnetwork-server.toml`:

```toml
enabled = true
transport = "WSS"
bindHost = "0.0.0.0"
publicPort = 25565
pathPrefix = "miguelnetwork-v1"
certificate = "/absolute/path/to/fullchain.pem"
privateKey = "/absolute/path/to/private-key.pem"
allowBuiltInSelfSigned = false
```

The generated wstunnel restriction follows the actual `server-port` and only permits TCP forwarding to that port on
loopback. Port 25566 is the MiguelNetwork client protocol convention; a different port is only supported by setting the
same development JVM override on both sides. Keep the Minecraft listener itself bound to loopback or a trusted internal
network.

On Windows, certificate paths may use forward slashes (`C:/certs/fullchain.pem`) to avoid confusing TOML escaping.

## Client

The generated `config/miguelnetwork-client.toml` is usable without editing:

```toml
enabled = true
pathPrefix = "miguelnetwork-v1"
maxTunnelProcesses = 8
```

For each previously unseen Minecraft address, the client performs a short, protocol-valid wstunnel Upgrade probe in this
order:

1. WSS with normal trusted-certificate and hostname verification;
2. plain WS;
3. unchanged vanilla Minecraft TCP when neither Upgrade probe succeeds.

Successful WSS and WS results are cached for the lifetime of the game. A negative TCP result is cached for 30 seconds,
so a server that is still starting can later be detected without restarting the client. Each discovered WebSocket
endpoint uses its own sidecar; least-recently-used sidecars are stopped at `maxTunnelProcesses`.

The remote Minecraft destination is the MiguelNetwork protocol convention `127.0.0.1:25566`. It is intentionally not a
client option. A server using the unmodified bundled wstunnel must therefore bind Minecraft to port 25566 as shown above.
The public address and port entered by the player remain unrelated to this internal port.

Automatic WS fallback is opportunistic rather than downgrade-proof on first contact. A network attacker able to block a
new endpoint's valid WSS service and impersonate its plain WS service could cause the first discovery to choose WS. The
client logs a prominent warning whenever WS is selected. Production deployments should expose only WSS and block direct
public access to the WS backend.

## Isolated development only

The server can explicitly allow wstunnel's built-in self-signed certificate with
`allowBuiltInSelfSigned = true`. Automated local tests may pair this with the development-only JVM property
`-Dmiguelnetwork.tls.verify=false`. Both settings emit prominent warnings and must never be used for a public endpoint.

JVM system properties documented in `TECHNICAL_VALIDATION.md` remain available for automated validation and override
the TOML values.

Restart Minecraft or the dedicated server after changing transport settings. Live sidecar reconfiguration is not part
of the current Alpha release.

## Plain WebSocket and reverse proxies

Server-side `transport = "WS"` selects unencrypted WebSocket. This is useful for an isolated development test, or behind
a TLS-terminating reverse proxy such as Nginx. The client automatically discovers the public WSS side of that layout.

Never expose a production `WS` listener directly to the internet. Both sides log a prominent warning when WS is used.
