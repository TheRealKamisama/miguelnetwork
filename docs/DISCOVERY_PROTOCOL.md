# MiguelNetwork Discovery Protocol v1

Status: experimental, implemented by MiguelNetwork `0.1.0-alpha.3`.

The protocol lets a Minecraft client turn the address entered by the player into a per-server transport route. It is a
control plane only: Minecraft and wstunnel data never pass through the Discovery HTTP handler.

## Exchange

The client sends an HTTPS request to the same logical endpoint that the player entered:

```http
POST /.well-known/miguelnetwork/v1 HTTP/1.1
Host: play.example.net:35548
Content-Type: application/json

{"protocol":"miguel-discovery/1","nonce":"<base64url 32 random bytes>"}
```

The server returns a JSON signature envelope:

```json
{
  "payload": "<base64url exact UTF-8 manifest bytes>",
  "signature": "<base64url Ed25519 signature over payload bytes>",
  "publicKey": "<base64url X.509 SubjectPublicKeyInfo>"
}
```

The decoded payload has this shape:

```json
{
  "protocol": "miguel-discovery/1",
  "serverId": "persistent UUID",
  "audience": "play.example.net:35548",
  "clientNonce": "<the request nonce>",
  "configEpoch": 1,
  "issuedAt": 1788600000,
  "expiresAt": 1788600120,
  "routes": [
    {
      "id": "zstdnet-primary",
      "transport": "WSS",
      "host": "play.example.net",
      "port": 35548,
      "pathPrefix": "miguelnetwork-v1",
      "wstunnelTargetPort": 25565,
      "priority": 200,
      "filters": [
        {"id": "zstdnet-stream", "version": 1, "required": true}
      ]
    },
    {
      "id": "minecraft-primary",
      "transport": "WSS",
      "host": "play.example.net",
      "port": 35548,
      "pathPrefix": "miguelnetwork-v1",
      "wstunnelTargetPort": 25566,
      "priority": 100,
      "filters": []
    }
  ],
  "keyId": "<base64url SHA-256 of publicKey>"
}
```

Routes are considered in descending priority. A client skips a route when it does not implement a required filter.
Unknown optional filters may be ignored. The target port is intentionally part of the signed manifest: stock wstunnel
requires the client to place it in the tunnel request, while the player never sees or configures it.

## Validation and persistent state

The client must validate all of the following before using a route:

- the public HTTPS certificate and hostname;
- the Ed25519 signature over the exact decoded payload bytes;
- `keyId`, `protocol`, `audience`, request nonce and validity interval;
- the previously pinned `serverId` and public key;
- a `configEpoch` not lower than the highest value previously accepted.

The first key is trusted only after a normal authenticated HTTPS exchange and is then pinned in
`config/miguelnetwork/client-trust.json`. The private Ed25519 identity is generated once on the server at
`config/miguelnetwork/generated/discovery-identity.key`; losing it looks like an identity attack to existing clients.
Back up that file and do not publish it.

Once a client has authenticated a WSS route, or successfully used legacy WSS, it records a WSS security floor. A later
Discovery failure may retry WSS but may not silently downgrade that endpoint to WS or raw TCP. This protects returning
clients; no protocol can provide equivalent downgrade protection for a brand-new endpoint without an authenticated
bootstrap such as HTTPS.

## Reverse proxy contract

The Mod serves plain HTTP on a configurable internal address. TLS and path multiplexing normally happen at Nginx:

```nginx
location = /.well-known/miguelnetwork/v1 {
    proxy_pass http://192.168.0.146:25567;
    proxy_http_version 1.1;
    proxy_set_header Host $http_host;
    proxy_set_header X-Forwarded-Host $http_host;
    proxy_set_header X-Forwarded-Proto https;
}

location ^~ /miguelnetwork-v1/ {
    proxy_pass http://192.168.0.146:35548;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $http_host;
    proxy_read_timeout 86400s;
    proxy_send_timeout 86400s;
}
```

`$http_host` is important when the public endpoint uses a non-default port. If Nginx and Minecraft run on different
machines or containers, set `discovery.bindHost` to a firewall-protected address reachable from Nginx.

## Compatibility fallback

For servers that do not implement v1, `legacyFallback = true` retains alpha.2 probing (WSS, then WS, then TCP). Legacy
mode still uses the conventional target port 25566 and cannot negotiate filters. It is a migration path, not part of
the signed protocol.
