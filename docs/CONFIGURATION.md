# Configuration

MiguelNetwork uses NeoForge TOML configuration. Both sides are disabled by default, and an empty client allowlist routes
nothing.

## Dedicated server

First move the internal Minecraft listener away from the public WSS port in `server.properties`:

```properties
server-ip=127.0.0.1
server-port=25566
```

After the first server launch, edit `config/miguelnetwork-server.toml`:

```toml
enabled = true
bindHost = "0.0.0.0"
publicPort = 25565
targetPort = 0
pathPrefix = "miguelnetwork-v1"
certificate = "/absolute/path/to/fullchain.pem"
privateKey = "/absolute/path/to/private-key.pem"
allowBuiltInSelfSigned = false
```

`targetPort = 0` follows the actual port from `server.properties`. The generated wstunnel restriction only permits TCP
forwarding to that port on loopback. Keep the Minecraft listener itself bound to loopback or a trusted internal network.

On Windows, certificate paths may use forward slashes (`C:/certs/fullchain.pem`) to avoid confusing TOML escaping.

## Client

After the first client launch, edit `config/miguelnetwork-client.toml`:

```toml
enabled = true
allowedServers = ["mc.example.com:25565"]
targetPort = 25566
pathPrefix = "miguelnetwork-v1"
verifyCertificate = true
maxTunnelProcesses = 8
```

Players continue entering the normal public Minecraft address. Accepted allowlist forms are:

- `mc.example.com` — all ports for exactly this host;
- `mc.example.com:25565` — one host and port;
- `[2001:db8::1]:25565` — one IPv6 endpoint;
- `*.example.com` — subdomains, but not the apex domain;
- `*` — every multiplayer connection (not recommended).

Host matching is case-insensitive. An address not on the allowlist uses Minecraft's normal TCP path unchanged.
Each active WSS endpoint uses its own sidecar so concurrent multiplayer status Pings cannot interrupt one another. The
least-recently-used sidecar is stopped when `maxTunnelProcesses` is reached.

## Isolated development only

The server can explicitly allow wstunnel's built-in self-signed certificate with
`allowBuiltInSelfSigned = true`. The corresponding client must set `verifyCertificate = false`. Both settings emit
prominent warnings and must never be used for a public endpoint.

JVM system properties documented in `TECHNICAL_VALIDATION.md` remain available for automated validation and override
the TOML values.

Restart Minecraft or the dedicated server after changing transport settings. Live sidecar reconfiguration is not part
of the current Alpha release.
