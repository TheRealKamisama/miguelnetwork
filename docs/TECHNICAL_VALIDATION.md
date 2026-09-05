# Phase 0 technical validation

The generated NeoForge TOML configurations disable MiguelNetwork sidecars by default so that the game can be launched
before certificates are provisioned. JVM system properties may override those values for automated validation.

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
-Dmiguelnetwork.client.allowedServers=127.0.0.1:25565
-Dmiguelnetwork.target.port=25566
```

For a loopback-only development test, wstunnel's built-in self-signed certificate may be enabled explicitly:

```text
# server only
-Dmiguelnetwork.tls.allowBuiltInSelfSigned=true

# client only
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

# client
-Dmiguelnetwork.client.transport=WS
```

Direct WS exposure is for temporary testing only. In a TLS-terminating reverse-proxy deployment, the server uses WS
while the client remains on the default WSS transport.

## Current Phase 0 limitations

- SRV behavior, credentials and custom CA bundles are not implemented.
- Plain WS support exists for a reverse-proxy backend, but Nginx interoperability has not yet been validated.
- The current readiness contract uses the pinned v10.7.1 log messages and process exit state.
- A JVM hard crash can leave the child process alive.
- The JAR embeds the official v10.7.1 Windows/Linux x64 binaries. This remains a technical preview and has not yet
  completed a public-server compatibility or security review.
