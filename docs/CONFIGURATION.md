# Configuration and deployment

This guide applies to MiguelNetwork 0.2.0. For the shorter installation and
runtime-log checklist, see [`USER_GUIDE.md`](USER_GUIDE.md).

MiguelNetwork generates `config/miguelnetwork-server.toml` and `config/miguelnetwork-client.toml` through Forge.
The server configuration is a COMMON config so it is created in the documented top-level `config` directory, not under
a world's `serverconfig` directory.

On this Java 17 branch, the standalone gateway admits at most 128 simultaneous
connections and uses at most 256 daemon workers for both directions. Excess
connections are closed; the limit includes short-lived Discovery requests.

The Mod reads the running Minecraft port and `server-ip` from `server.properties`. A blank/wildcard `server-ip` becomes
the loopback target `127.0.0.1`; an explicit address is preserved. Supported ZstdNet versions (currently 1.4.7 and
1.4.8) are detected reflectively and their listen port becomes a higher-priority route. The resolved values are written
on every start to
`config/miguelnetwork/generated/detected-server.toml`; this generated file is diagnostic and must not be edited.

`publicPort` cannot equal `server-port` or the detected ZstdNet port: the public gateway/wstunnel listener and the
Minecraft backend are different listeners and cannot bind the same address and port.

## Mode 1: standalone (default)

Use this when the Minecraft machine can expose one additional TCP port and no Nginx is wanted. MiguelNetwork starts a
private wstunnel listener on a dynamic loopback port, then starts its built-in gateway on `publicPort`. The gateway
answers Discovery itself and forwards every other HTTP/WebSocket connection byte-for-byte to wstunnel.

Example `server.properties`:

```properties
server-ip=127.0.0.1
server-port=25567
```

Generated/default `config/miguelnetwork-server.toml`:

```toml
enabled = true
mode = "STANDALONE"
bindHost = "0.0.0.0"
publicPort = 35548
pathPrefix = "miguelnetwork-v1"

[discovery]
enabled = true
signResponses = false
bindHost = "127.0.0.1" # ignored in standalone mode
port = 25568            # ignored in standalone mode
advertisedTransport = "WS" # standalone is always WS
advertisedHost = ""
advertisedPort = 0
configEpoch = 1
validitySeconds = 120
```

Deployment steps:

1. Install Forge, MiguelNetwork and optionally the supported ZstdNet on both client and server.
2. Keep the Minecraft/ZstdNet backend port private; expose TCP `35548` (or the configured `publicPort`) on the firewall
   and NAT it directly to the Minecraft machine.
3. Start the server. Confirm the log says `standalone WS gateway is ready` and inspect `detected-server.toml`.
4. Add `host:35548` to the client server list. No client target port, WS/WSS choice or server allowlist is required.

Standalone deliberately uses plain WS. It does not generate or manage certificates.

## Mode 2: external Nginx proxy

Use this when an existing gateway should provide TLS/WSS. MiguelNetwork exposes two private upstreams: wstunnel on
`bindHost:publicPort` and Discovery on `discovery.bindHost:discovery.port`. Nginx combines them on its public port.

Example server config:

```toml
enabled = true
mode = "EXTERNAL_PROXY"
bindHost = "192.168.0.146"
publicPort = 35549
pathPrefix = "miguelnetwork-v1"

[discovery]
enabled = true
signResponses = false
bindHost = "192.168.0.146"
port = 25568
advertisedTransport = "WSS"
advertisedHost = "kraber.top"
advertisedPort = 35548
configEpoch = 1
validitySeconds = 120
```

Matching Nginx server locations:

```nginx
location = /.well-known/miguelnetwork/v1 {
    proxy_pass http://192.168.0.146:25568;
    proxy_http_version 1.1;
    proxy_set_header Host $http_host;
    proxy_set_header X-Forwarded-Host $http_host;
    proxy_set_header X-Forwarded-Proto https;
}

location ^~ /miguelnetwork-v1/ {
    proxy_pass http://192.168.0.146:35549;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $http_host;
    proxy_read_timeout 86400s;
    proxy_send_timeout 86400s;
}
```

Deployment steps:

1. Install the Mod and set `mode = "EXTERNAL_PROXY"`; bind both upstreams only to loopback or a protected LAN.
2. Configure Nginx TLS and the two locations above. The private Nginx-to-wstunnel hop remains WS.
3. Set `advertisedTransport = "WSS"`, and set the public host/port explicitly or leave them empty/zero to derive them
   from the preserved Host header.
4. Expose only Nginx's public port. Restart the Minecraft server after changing transport settings.

## Optional security controls

WSS and Discovery signatures are independent optional controls and are disabled by default at the protocol policy
level. Normal WSS certificate verification remains enabled whenever a WSS route is actually selected.

Server:

```toml
[discovery]
signResponses = true
```

Client:

```toml
[security]
verifyDiscoverySignatures = true
enforceWssDowngradeProtection = true
verifyTlsCertificates = true
```

When signing is enabled, the server creates `config/miguelnetwork/generated/discovery-identity.key`. Clients that enable
verification pin this identity and reject configuration rollback. Enable `signResponses` and
`verifyDiscoverySignatures` together. Existing trust data remains in `config/miguelnetwork/client-trust.json`, but it
does not force WSS while `enforceWssDowngradeProtection = false`.

## Client defaults

```toml
enabled = true
pathPrefix = "miguelnetwork-v1"
maxTunnelProcesses = 8
legacyFallback = true

[security]
verifyDiscoverySignatures = false
enforceWssDowngradeProtection = false
verifyTlsCertificates = true
```

The client tries Discovery on the logical endpoint over HTTPS and then HTTP. The returned route supplies its public
WS/WSS endpoint and the automatically detected backend host/port. If Discovery is absent, the legacy WSS → WS → TCP
probe remains available; only that compatibility path retains the old fixed target-port convention.

## Verifying an installed deployment

After restarting both sides, inspect `logs/latest.log` on the server and client.
The server should report `standalone WS gateway is ready` and
`Discovery and wstunnel share one port`. The client should report `selected Discovery
route`. A `Discovery unavailable`, `selected legacy ... route`, or
`discovered vanilla TCP endpoint` message means that Discovery was unavailable and
the compatibility path was used or the connection stayed on vanilla TCP. Set
`legacyFallback = false` to make that condition fail closed instead of silently
downgrading.
