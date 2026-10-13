# Native ZstdNet / MiguelNetwork interoperability v1 proposal

Status: accepted implementation plan. The maintained ports now implement the
contract documented in [NATIVE_INTEROP_V1.md](NATIVE_INTEROP_V1.md); validation
results are recorded separately. The inspected baselines below remain historical.

## Scope and inspected baselines

Implement the same small contract in both maintained platform pairs:

| Mod | Platform | Inspected branch / revision |
| --- | --- | --- |
| MiguelNetwork | Forge 1.7.10 / Java 25 | `codex/forge-1.7.10-java25`, `9eceea5` |
| ZstdNet | Forge 1.7.10 / Java 25 | `codex/1.7.10-java25`, `845b490` |
| MiguelNetwork | Cleanroom 1.12.2 / Java 25 | `codex/cleanroom-1.12.2`, `d37a953` |
| ZstdNet | Cleanroom 1.12.2 / Java 25 | `cleanroom-1.12.2`, `4df9154` |

The existing MiguelNetwork Cleanroom worktree is detached at the older
`6df68c3`; its branch tip, inspected with `git show`, is the implementation
baseline. Preserve existing work and recheck these revisions before editing.

The 1.7.10 MiguelNetwork platform has no ZstdNet adapter: its route selector
rejects required filters and its server currently supplies an empty detected
ZstdNet port. The latest 1.12.2 branch has a reflective adapter gated to
`1.4.7-cleanroom-alpha.1`. It injects into `ClientProxy.startConnection`, writes
private GUI/session/settings fields, and can return to the original ZstdNet path
after a route-preparation error.

Both ZstdNet ports already have a `LocalZstdNet.start` overload separating the
physical upstream, status upstream, and presented Minecraft handshake address.
Their server listeners accept Zstd streams for login and plain Minecraft status
requests. Reuse those behaviors.

Replace the cross-mod Mixin and private reflection on 1.12.2; add native support
on 1.7.10. Keep the vanilla-facing Mixins needed by each mod. Other Minecraft
platforms and their existing compatibility adapters are outside this change.

## Transport composition

```text
Minecraft client
  -> ZstdNet client proxy (compress login; preserve logical handshake)
  -> MiguelNetwork / wstunnel client TCP listener
  -> WS, or operator-selected WSS
  -> wstunnel server
  -> ZstdNet server listener (decompress)
  -> Minecraft backend
```

Retain Discovery v1 and its existing filter:

```json
{"id":"zstdnet-stream","version":1,"required":true}
```

The local integration API major and the stream filter version are separate
contracts, even though both initially equal 1. No additional packet, stream
preamble, public HTTP endpoint, or Minecraft plugin-channel handshake is needed.
STANDALONE/plain WS and unsigned Discovery retain their current defaults.

## Minimal local API

Each mod publishes one common-side facade:

```java
public static Map<String, Object> negotiate(int requestedApiMajor);
```

Proposed facade classes:

- ZstdNet: `cn.tohsaka.factory.zstdnet.interop.NativeBridge`
- MiguelNetwork: `io.github.therealkamisama.miguelnetwork.interop.NativeBridge`

The other mod looks up this public factory once when present. Calls thereafter
use the returned JDK callbacks. No private reflection, `setAccessible`, shared
third mod, duplicate API classes, or compile-time dependency on the other mod is
required. This is an in-process API; its maps and callbacks are not serialized.

The descriptor is immutable and includes `apiMajor` (integer 1),
`minecraftVersion` (exact platform string), and the following side-appropriate
callbacks. ZstdNet additionally declares `filters = {"zstdnet-stream": 1}`.
An unsupported API major returns no descriptor. Missing callbacks or invalid
types mean that capability is unavailable; malformed responses during an active
native operation are errors. The common facade must not load client-only
Minecraft classes on a dedicated server.

| Publisher / callback | JDK signature | Contract |
| --- | --- | --- |
| ZstdNet / `watchServer` | `Function<Consumer<Map<String,Object>>, AutoCloseable>` | Subscribe to effective server state; immediately report a snapshot; returned handle unsubscribes. |
| ZstdNet / `openStream` | `Function<Map<String,Object>, Map<String,Object>>` | Create an independent local Zstd proxy using supplied physical and logical endpoints. |
| ZstdNet / `ownsEndpoint` | `Predicate<InetSocketAddress>` | Identify an actual live local proxy listener, including proxies opened through ZstdNet's original direct path. |
| MiguelNetwork / `prepareConnection` | `Function<Map<String,Object>, Map<String,Object>>` | Resolve Discovery and prepare the complete client connection, composing ZstdNet only for a compatible advertised filter. |

### Server state

Each snapshot contains:

- `generation`: monotonically increasing long for effective state changes.
- `ready`: boolean; false for disabled, unbound, failed, or stopped listeners.
- When ready, `listener`: connectable `InetSocketAddress` for the actual bound
  ZstdNet listener, and `backend`: actual Minecraft target address.

ZstdNet supplies effective runtime endpoints, including auto-takeover and LAN
port changes. A configured port or a pre-bind allocation plan is not readiness.
Wildcard binds are translated into an address MiguelNetwork can actually dial.
Callbacks are delivered in order and outside ZstdNet's lifecycle lock.

MiguelNetwork uses this state for Discovery targets and wstunnel restrictions.
Publish a filtered route only after the endpoint is ready and forwarding to it
has been allowed. Withdraw it when unavailable. Keep any explicitly advertised
unfiltered route under the existing policy. Do not infer or open an unadvertised
backend after a filtered route fails.

Endpoint changes must update forwarding restrictions as well as Discovery.
Implementation must verify whether the pinned wstunnel reloads restrictions;
if a sidecar restart is required, document and test the resulting reconnect.
Existing remote Discovery caches retain their normal bounded expiry.

### Client preparation and ownership

`prepareConnection` receives `logical` (`InetSocketAddress`, retaining the
hostname) and an optional `cancelled` (`BooleanSupplier`). It returns either:

- `DECLINED`: no native connection was selected, such as MiguelNetwork being
  disabled or an allowed legacy direct path. ZstdNet retains its original path.
- `READY`: `address` (local socket destination), `lease` (`AutoCloseable`),
  `filtered` (boolean), and, for a filtered connection, `stats`
  (`Supplier<Map<String,Object>>`) for ZstdNet's existing HUD/report consumers.

Rejections and preparation failures propagate as errors. A rejected Discovery
manifest, unknown required filter, disabled legacy fallback, or failed selected
route never becomes `DECLINED`. An explicitly advertised unfiltered route may
produce `READY` without a Zstd proxy; it must not be compressed speculatively.

To compose a filtered route, MiguelNetwork first prepares its wstunnel lease,
then calls ZstdNet's `openStream` with:

- `upstream`: the physical wstunnel client TCP address.
- `statusUpstream`: the status TCP address, initially the same address.
- `presented`: the original logical Minecraft host/port.
- `udpUpstream`: the original direct UDP destination, when applicable.

`openStream` returns `address`, `lease`, and `stats`. It forces stream mode
`ZSTD` for login rather than probing with `AUTO`; raw status remains ZstdNet's
responsibility. ZstdNet applies its own configured compression level and keeps
compression, packet handling, and statistics within its implementation.

MiguelNetwork returns one composite lease that closes the Zstd proxy before
releasing its wstunnel lease. Closing is idempotent. Failed or cancelled
preparation disposes every partially created resource. ZstdNet's own client code
adopts this session through ordinary method calls and retains GUI, settings,
attempt-generation, HUD, and disconnection ownership.

The normal MiguelNetwork socket hook also uses the same coordinator, so status
requests can use an advertised filtered route without the ZstdNet GUI intercept.
Each socket/attempt has its own lease; status completion cannot close gameplay.
Promote the 1.7.10 lease model into the 1.12.2 implementation as needed.

The socket hook bypasses endpoints actually owned by either local proxy. Remove
ownership when a listener closes; do not bypass every loopback address or use a
global next-connect flag. This prevents repeated Discovery/tunneling of the
internal connection.

## UDP boundary

`LocalZstdNet.start` currently uses its TCP upstream as the UDP destination too.
For a composed connection that would incorrectly aim UDP at a TCP-only wstunnel
loopback port. Separate this destination when opening a native stream. Preserve
direct UDP where the deployment exposes it; do not claim UDP or voice transport
through WS/WSS. Adding UDP-over-WebSocket is outside this protocol.

## Implementation and validation gates

1. Add the same public factory/schema and contract tests to both platform pairs.
   Check protocol/platform mismatch, malformed payloads, and absent peers.
2. Add effective listener notifications and update server Discovery/restrictions
   from live state. Test both mod startup orders, bind failure, disabled proxy,
   auto-takeover backend selection, and existing listener restart paths.
3. Add composite client leases and route-aware Zstd sessions. Test raw versus
   filtered advertised routes, handshake hostname/port preservation, original
   UDP destination, cancellation/failure cleanup, reconnect, simultaneous status
   and gameplay, and exact owned-endpoint bypass.
4. Remove the 1.12.2 cross-mod Mixin and private adapter after native tests pass.
   Update platform documentation and API-based compatibility claims.
5. Build all four production JARs with their checked-in wrappers. Verify 1.7.10
   reobfuscated outputs and 1.12.2 remapped outputs, then record artifact hashes.
6. Run paired production server/client checks on Windows for both game versions,
   including first-class plain WS, unsigned Discovery, status, login, reconnect,
   cancellation, HUD/report continuity, each mod alone, and fail-closed errors.
   Report runtime checks separately from unit/build checks and any unrun scope.

No commit, push, tag, publication, or GitHub login is part of this proposal.
