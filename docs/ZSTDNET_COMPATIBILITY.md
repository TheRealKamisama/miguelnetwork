# ZstdNet compatibility

MiguelNetwork `0.1.0-alpha.4` contains a unilateral compatibility adapter for ZstdNet `1.4.7` on Minecraft 1.21.1
NeoForge. ZstdNet itself and its JAR are not modified, and no Zstd compression code is copied.

## Byte-stream composition

For login traffic, the resulting chain is:

```text
Minecraft client
  -> ZstdNet client loopback proxy
  -> MiguelNetwork/wstunnel client loopback listener
  -> WSS
  -> wstunnel server
  -> ZstdNet server listener
  -> Minecraft backend
```

MiguelNetwork sees and transports only the already-compressed TCP byte stream. Server-list status traffic can select
the lower-priority raw route and therefore does not require Minecraft packet parsing in MiguelNetwork.

When compatible ZstdNet is loaded on the server, MiguelNetwork reads its public `readListenPort()` API reflectively,
adds that port to the generated wstunnel restriction, and advertises a higher-priority route with the required
`zstdnet-stream` filter. A second raw route targets the Minecraft backend, so clients without ZstdNet still have a
usable MiguelNetwork route.

On the client, ZstdNet 1.4.7's coremod normally creates its loopback proxy before Minecraft reaches
`Connection.connect`. MiguelNetwork uses an optional pseudo-mixin on ZstdNet's hook class, creates the wstunnel route
first, invokes ZstdNet's public `LocalZstdNet.start(...)` API reflectively, and publishes the returned handle back to
ZstdNet so its normal UI/login/logout lifecycle remains responsible for closing it. Injecting into the hook class
avoids relying on transformation order between ZstdNet's coremod and MiguelNetwork's mixins. The one subsequent
loopback connection is marked as internal to prevent double tunnelling.

## Version and failure policy

The reflective adapter is enabled only when the installed Mod metadata version is exactly `1.4.7` and the expected
classes, methods and fields are present. This narrow gate is deliberate because ZstdNet exposes no stable integration
SPI for replacing the upstream address and adopting an externally created `ProxyHandle`.

If the signed manifest requires `zstdnet-stream` but the adapter is unavailable, that route is skipped. A compatible
raw route may still be selected. A signed manifest with no compatible route fails closed rather than connecting to an
unadvertised backend. Unknown ZstdNet versions use their original hook and log a compatibility warning; they are not
claimed as supported until their exact API has been tested.

The only private reflection in the 1.4.7 adapter is the narrow handoff to `ConnectScreenHooks.currentProxy` and its
lock. Compression, framing, status probing, statistics and proxy shutdown continue to be owned by ZstdNet.
