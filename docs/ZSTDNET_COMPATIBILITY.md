# ZstdNet Cleanroom compatibility

MiguelNetwork `0.2.0` includes an optional reflective adapter for the verified
ZstdNet Cleanroom `1.4.7-cleanroom-alpha.1` API on Minecraft 1.12.2 / Cleanroom / Java 25.
ZstdNet and its JAR are not modified, and MiguelNetwork does not copy or reimplement Zstd compression.

## Byte-stream composition

For login traffic, the resulting chain is:

```text
Minecraft client
  -> ZstdNet client loopback proxy
  -> MiguelNetwork/wstunnel client loopback listener
  -> WS or WSS
  -> wstunnel server
  -> ZstdNet server listener
  -> Minecraft backend
```

MiguelNetwork transports only the already-compressed TCP byte stream. Status traffic is still handled by ZstdNet's
own proxy and server runtime; MiguelNetwork does not parse Minecraft packets.

On a server with the verified ZstdNet version, MiguelNetwork reads the public
`ServerProxyConfigFile.readListenPort()` API reflectively. When ZstdNet's dedicated-server `auto_takeover` plan is
active, it also records that plan's resolved listener and backend ports. The detected listener is added to the
generated wstunnel restriction, and a higher-priority `zstdnet-primary` route with the required `zstdnet-stream` filter
is advertised. The ordinary `minecraft-primary` route remains available for clients without ZstdNet; a disabled
ZstdNet server proxy is not advertised.

On a client, the optional pseudo-mixin enters ZstdNet's `ClientProxy.startConnection` lifecycle. When Discovery selects
the required filter, MiguelNetwork prepares its wstunnel route first, invokes ZstdNet's public
`LocalZstdNet.start(...)` overload with the original logical host/port as the presented handshake address, publishes the
returned `ProxyHandle` to ZstdNet's normal lifecycle, and constructs its internal loopback `GuiConnecting` under
ZstdNet's scoped `InternalConnectGuard`. The loopback port is consumed once by MiguelNetwork so the connection is not
tunneled a second time.

## Version and failure policy

The adapter is enabled only when the loaded Mod metadata version is exactly
`1.4.7-cleanroom-alpha.1` and the expected classes, methods and fields are present. This narrow gate is deliberate:
ZstdNet does not expose a stable integration SPI for replacing its upstream address and adopting an externally created
proxy handle.

If a Discovery manifest requires `zstdnet-stream` but the adapter is unavailable, that route is skipped. An advertised
unfiltered route may still be selected. A manifest with no compatible route fails closed rather than connecting to an
unadvertised backend. Unknown ZstdNet versions retain ZstdNet's original path and log a compatibility warning; they are
not claimed as supported until their exact API is tested.

The adapter owns no compression, framing, status probing, statistics or proxy shutdown. Those remain ZstdNet's
responsibility. MiguelNetwork's default STANDALONE/plain-WS deployment and optional external-proxy WSS policy are
unchanged.
