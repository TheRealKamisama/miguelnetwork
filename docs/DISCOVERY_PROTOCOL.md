# MiguelNetwork Discovery Protocol v1

Status: experimental, implemented by MiguelNetwork `0.1.0-alpha.6`.

Discovery turns the logical Minecraft address entered by a player into a transport route. It is a control plane only:
Minecraft and wstunnel stream bytes do not pass through the Discovery document generator. The same public port may be
served by MiguelNetwork's standalone gateway or by an external reverse proxy.

## Exchange

The client tries HTTPS and then HTTP on the logical endpoint:

```http
POST /.well-known/miguelnetwork/v1 HTTP/1.1
Host: play.example.net:35548
Content-Type: application/json

{"protocol":"miguel-discovery/1","nonce":"<base64url 32 random bytes>"}
```

With the default `signResponses = false`, the response is the manifest itself:

```json
{
  "protocol": "miguel-discovery/1",
  "serverId": "unsigned",
  "audience": "play.example.net:35548",
  "clientNonce": "<the request nonce>",
  "configEpoch": 1,
  "issuedAt": 1788600000,
  "expiresAt": 1788600120,
  "routes": [
    {
      "id": "zstdnet-primary",
      "transport": "WS",
      "host": "play.example.net",
      "port": 35548,
      "pathPrefix": "miguelnetwork-v1",
      "wstunnelTargetHost": "127.0.0.1",
      "wstunnelTargetPort": 25566,
      "priority": 200,
      "filters": [
        {"id": "zstdnet-stream", "version": 1, "required": true}
      ]
    },
    {
      "id": "minecraft-primary",
      "transport": "WS",
      "host": "play.example.net",
      "port": 35548,
      "pathPrefix": "miguelnetwork-v1",
      "wstunnelTargetHost": "127.0.0.1",
      "wstunnelTargetPort": 25567,
      "priority": 100,
      "filters": []
    }
  ],
  "keyId": "unsigned"
}
```

`wstunnelTargetHost` is new in alpha.6 and defaults to `127.0.0.1` when reading older v1 documents. It allows a server
bound to an explicit `server-ip` to advertise that local target without any client configuration. The wstunnel server's
generated restriction still limits requests to the detected host and ports.

Routes are considered in descending priority. A client skips a route when it does not implement a required filter.
Unknown optional filters may be ignored.

## Optional signed envelope

When the server enables `discovery.signResponses`, it wraps the exact manifest bytes:

```json
{
  "payload": "<base64url exact UTF-8 manifest bytes>",
  "signature": "<base64url Ed25519 signature over payload bytes>",
  "publicKey": "<base64url X.509 SubjectPublicKeyInfo>"
}
```

A client with `security.verifyDiscoverySignatures = true` requires this envelope and validates the signature, `keyId`,
protocol, audience, nonce, validity, pinned server identity and monotonic `configEpoch`. A client with verification off
accepts either the raw manifest or an envelope without using the signature as a trust boundary.

The private identity lives at `config/miguelnetwork/generated/discovery-identity.key`. It is generated only when signing
is enabled. Losing it causes a key-change rejection on clients that previously pinned it.

WSS downgrade protection is separately controlled by `security.enforceWssDowngradeProtection`; it is disabled by
default. When enabled, a successfully used WSS endpoint establishes a persistent floor in
`config/miguelnetwork/client-trust.json`.

Nonce, audience and validity checks remain active for unsigned documents. They prevent accidental cache/cross-endpoint
reuse, but without TLS and a verified signature they do not provide authentication against an active network attacker.
This matches the default unencrypted Minecraft transport threat model.

## Public-port multiplexing

In `STANDALONE` mode the built-in gateway owns the public plain-HTTP/WS port. It handles the exact Discovery path and
forwards all other connections, including `/miguelnetwork-v1/...`, unchanged to a private dynamic wstunnel listener.

In `EXTERNAL_PROXY` mode an external gateway performs the same split. A minimal Nginx mapping is:

```nginx
location = /.well-known/miguelnetwork/v1 {
    proxy_pass http://192.168.0.146:25568;
    proxy_http_version 1.1;
    proxy_set_header Host $http_host;
    proxy_set_header X-Forwarded-Host $http_host;
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

Preserve `$http_host` so a non-default public port remains in the audience. The external gateway may expose either WS
or WSS; set `discovery.advertisedTransport` to match it.

## Compatibility fallback

When v1 is absent, `legacyFallback = true` retains WSS → WS → TCP probing. Legacy mode cannot negotiate a target host,
port or stream filter and therefore keeps the historical fixed target port. It is a migration path, not Discovery v1.
