# Phase 0 technical validation

The generated NeoForge server configuration disables its listener by default so that the game can be launched before
certificates are provisioned. Client transport discovery is enabled by default. JVM system properties may override
values for automated validation.

For a WSS server validation run, configure `run/server/server.properties` as:

```properties
server-ip=127.0.0.1
server-port=25566
```

Then provide these JVM system properties to the server run:

```text
-Dmiguelnetwork.server.enabled=true
-Dmiguelnetwork.public.port=25565
-Dmiguelnetwork.target.port=25566
-Dmiguelnetwork.tls.certificate=<absolute-fullchain.pem>
-Dmiguelnetwork.tls.privateKey=<absolute-private-key.pem>
```

The client run needs:

```text
-Dmiguelnetwork.client.enabled=true
```

Normally no client properties are required. Tests may bypass discovery with
`-Dmiguelnetwork.client.transport=WSS` or `WS`; `-Dmiguelnetwork.target.port` remains a development-only compatibility
override for the fixed protocol target port.

For a loopback-only development test, wstunnel's built-in self-signed certificate may be enabled explicitly:

```text
# server only
-Dmiguelnetwork.tls.allowBuiltInSelfSigned=true

# client only; requires a forced WSS transport in local tests
-Dmiguelnetwork.tls.verify=false
```

Both settings produce prominent warnings. They must not be used for a public endpoint. Certificate verification remains
enabled by default, and the server refuses to start without configured certificate files unless the self-signed switch
is explicitly enabled.

For development only, either use the bundled binary or override it on both sides:

```text
-Dmiguelnetwork.wstunnel.path=<absolute-wstunnel-executable>
```

The player connects to the WSS endpoint as a normal Minecraft address, for example `mc.example.com:25565`.

For an explicitly unencrypted WS test, omit all TLS properties and add the following property to each process:

```text
# server
-Dmiguelnetwork.server.transport=WS

# client test override; normal clients discover this automatically
-Dmiguelnetwork.client.transport=WS
```

Direct WS exposure is for temporary testing only. In a TLS-terminating reverse-proxy deployment, the server uses WS
while the client remains on the default WSS transport.

## Current Phase 0 limitations

- SRV behavior, credentials and custom CA bundles are not implemented.
- Automatic discovery adds up to two 1.5-second probes to the first connection for an unknown endpoint; negative TCP
  discoveries are cached for 30 seconds.
- First-contact automatic fallback from WSS to WS is opportunistic and cannot provide cryptographic downgrade protection.
- The current readiness contract uses the pinned v10.7.1 log messages and process exit state.
- A JVM hard crash can leave the child process alive.
- The JAR embeds the official v10.7.1 Windows/Linux x64 binaries. This remains a technical preview and has not yet
  completed a public-server compatibility or security review.
