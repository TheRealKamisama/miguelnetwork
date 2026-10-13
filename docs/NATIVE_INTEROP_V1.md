# Native ZstdNet interoperability v1

This contract is implemented in the maintained Forge 1.7.10 / Java 25 and
Cleanroom 1.12.2 / Java 25 ports of both mods. Install updated builds of both
mods. An old ZstdNet JAR with the same release label does not acquire this API;
compatibility is negotiated through the public factory.

## Connection and deployment

Login follows Minecraft -> local ZstdNet proxy -> local wstunnel TCP listener ->
WS/WSS -> server wstunnel -> ZstdNet listener -> Minecraft backend. ZstdNet owns
compression, Minecraft framing and traffic statistics. MiguelNetwork owns
Discovery, route selection and tunnel leases.

Discovery v1 remains the control path. A compressed route declares:

```json
{"id":"zstdnet-stream","version":1,"required":true}
```

The local API major and stream filter version are independent contracts. No
extra stream preamble, plugin channel, certificate management or public HTTP
endpoint is introduced. STANDALONE/plain WS and unsigned Discovery remain the
defaults. WSS and signed Discovery remain optional.

## Public factory

Each mod provides `public static Map<String,Object> negotiate(int requestedApiMajor)`:

- ZstdNet: `cn.tohsaka.factory.zstdnet.interop.NativeBridge`
- MiguelNetwork: `io.github.therealkamisama.miguelnetwork.interop.NativeBridge`

One public reflection lookup discovers the optional peer. Subsequent calls use
JDK callbacks. Production interoperability does not access private fields,
inject into the other mod, require a shared third mod, or bundle the peer.

An immutable descriptor contains integer `apiMajor = 1`, the exact
`minecraftVersion`, and side-appropriate capabilities. Unsupported majors return
null. ZstdNet also declares `filters = {"zstdnet-stream": 1}`. Mod release labels
are not an allowlist. An API/platform mismatch or missing capability makes that
capability unavailable. Invalid responses during an active operation are errors.

| Publisher / key | Type | Meaning |
| --- | --- | --- |
| ZstdNet / `watchServer` | `Function<Consumer<Map<String,Object>>,AutoCloseable>` | Ordered effective server snapshots; returned handle unsubscribes. |
| ZstdNet / `openStream` | `Function<Map<String,Object>,Map<String,Object>>` | Independent compressor using supplied physical and logical endpoints. |
| ZstdNet / `ownsEndpoint` | `Predicate<InetSocketAddress>` | Exact live local proxy ownership. |
| MiguelNetwork / `prepareConnection` | `Function<Map<String,Object>,Map<String,Object>>` | Advertised route selection and complete client lease preparation. |

The common factory loads no client-only Minecraft classes on dedicated servers.
ZstdNet installs its client compression-level supplier during client initialization.

## Stream ordering agreement

WebSocket framing, HTTP upgrade and WS/WSS transport belong exclusively to
MiguelNetwork. ZstdNet implements only its TCP compression/RAW relay. The
resulting physical chain is:

```text
Minecraft -> ZstdNet -> MiguelNetwork -> WS/WSS -> MiguelNetwork -> ZstdNet -> Minecraft
```

Both descriptors include `streamLayer` and an
`agreeStreamOrder: Function<Map<String,Object>,Map<String,Object>>` callback.
The layer IDs are `zstdnet-stream` (role `compression`, before transport) and
`miguelnetwork-transport` (role `encapsulation`, after compression).

Either peer can initiate negotiation. It sends its `peerLayer` constraints and
optionally a proposed `outbound` list. Each derives a compatible order, and the
initiator requires the other peer to acknowledge the same outbound and inbound
directions. The accepted response is:

```json
{"agreed":true,"outbound":["zstdnet-stream","miguelnetwork-transport"],"inbound":["miguelnetwork-transport","zstdnet-stream"]}
```

Conflicting constraints, missing acknowledgement, reversed ordering, invalid
responses or a missing direction are rejected. Native connection preparation
and stream opening carry this agreement as `streamOrder`; prepared sessions
echo it. No compressor opens for an unagreed order. The order describes the
mod-stage placement; `filtered=false` selects a RAW ZstdNet relay instead of
compression for an advertised unfiltered route.


## Server state and route updates

A snapshot contains long `generation` and boolean `ready`. When ready, it also
contains `listener` and `backend`, both valid `InetSocketAddress` values. An
unavailable listener may still report its known backend, so a listener bind
failure/restart does not substitute the configured public port for an
auto-takeover backend. Closing the whole server clears that backend.

Only an actually bound listener is ready. Wildcard binds become connectable
loopback addresses. Notifications are ordered outside the runtime lifecycle
lock; subscribing schedules the current snapshot immediately.

MiguelNetwork updates both Discovery and forwarding restrictions. During an
endpoint change it withdraws the compressed route, atomically replaces the
generated restriction file, waits for wstunnel 10.7.1's
`Restrictions config file has been reloaded` acknowledgement, and then
publishes the new route. This supports either mod's startup order. A failed
rule update stops the gateway rather than advertising an unapplied target.

Generated restrictions retain separate host/port entries for Minecraft and
ZstdNet. New Discovery responses use effective backend/listener addresses.
Previously issued remote manifests retain their normal bounded expiry; stale
targets can fail, and do not authorize a different target.

Relevant logs:

- `MiguelNetwork negotiated ZstdNet native API v{} for Minecraft {}`
- `MiguelNetwork selected Discovery route {} for {}`
- `MiguelNetwork applied native ZstdNet server state generation {}`
- `MiguelNetwork native server route update failed; stopping gateway`

## Client request, response and ownership

`prepareConnection` receives `logical` (`InetSocketAddress`, preserving the
original hostname), the required `streamOrder` agreement, and optional
`cancelled` (`BooleanSupplier`).

- `disposition = DECLINED`: no native transport selected, for example a disabled
  MiguelNetwork client or an allowed legacy direct endpoint. ZstdNet retains its
  original direct path.
- `disposition = READY`: resolved `address`, `lease` (`AutoCloseable`),
  boolean `filtered`, and optional `connected` (`Runnable`). A filtered session
  also has `stats` (`Supplier<Map<String,Object>>`).

Invalid Discovery, unknown required filters, disabled legacy fallback and failed
selected routes propagate errors. They never become DECLINED. An explicitly
advertised unfiltered route remains uncompressed. An optional `zstdnet-stream`
hint is applied only when the local peer supports it; an absent compressor does
not prevent selecting that advertised route. A required filter still rejects
an unavailable peer.

For a filtered route MiguelNetwork prepares wstunnel, then calls `openStream`
with `upstream`, `statusUpstream`, `presented` and optional `udpUpstream`
addresses. The status address defaults to the TCP upstream. The original
logical address is presented in the Minecraft handshake, including its Forge
suffix. Login mode is explicitly ZSTD rather than AUTO; status is raw.

`openStream` returns a loopback `address`, `lease`, `stats`, and optional
`connected` callback to close the listener after adoption without closing play.

The composite lease closes ZstdNet before releasing its wstunnel reference.
Close is idempotent. Failed/cancelled preparation closes partial resources.
Each socket has its own lease and compressor; shared wstunnel processes have
reference counts. Completing a status request cannot close gameplay. Native
GUI code adopts sessions through normal calls and retains its own cancellation,
settings, HUD, report and disconnect lifecycle.

When a ZstdNet GUI adopts an unfiltered transport, a local RAW relay preserves
the original handshake instead of presenting the wstunnel loopback address.
It does not add compression. Internal loopback sockets are identified by live
ownership, not a global next-connect flag or a blanket loopback bypass. They
retain the managed-route marker needed for Forge 1.7.10 EOF reporting.

The statistics supplier returns `mode` (RAW/ZSTD), `remoteHost`, `remotePort`,
long raw/wire up/down byte totals and rates (`rawUpBytes`, `rawDownBytes`,
`wireUpBytes`, `wireDownBytes`, `rawUpRate`, `rawDownRate`, `wireUpRate`,
`wireDownRate`), and double `ratioPercent`.

## UDP and stream boundaries

TCP and UDP destinations are separate. A native stream's UDP relay uses the
supplied direct destination; omitting it disables UDP. It never points UDP at
the TCP-only wstunnel loopback listener. This does not add UDP or voice transport
through WS/WSS; the deployment must expose the direct UDP service.

The Cleanroom server now recognizes the existing four-byte Zstd magic before
attempting Minecraft packet parsing. Short open frames must not be interpreted
as a 40-byte raw packet and time out. This matches the 1.7.10 classifier and
does not change the stream format.

## Verification

Use each module's checked-in wrapper. Forge production output is `reobfJar`;
Cleanroom production output is the remapped JAR, not a dev/shadow-dev artifact.
Contract and functional tests cover API/platform mismatch, malformed responses,
lease cleanup, ordered live state, stream ownership, original UDP routing,
raw handshake adoption, filter rejection and concurrent leases. The Cleanroom
regression test checks a short open compressed handshake without waiting for
more client data or EOF.

Production-JAR stream fixtures exercise both startup orders, real wstunnel
reload acknowledgement, native status/play separation, preserved handshake,
statistics, cancellation, reconnect and route withdrawal. These byte-stream
fixtures are distinct from actual Minecraft loader/client acceptance. Record
game runtime evidence and any unrun platform scope separately.

See [the current validation record](NATIVE_INTEROP_V1_VALIDATION.md) for accepted
artifact hashes, actual game checks and unrun scope.
