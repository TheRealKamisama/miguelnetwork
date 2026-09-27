# MiguelNetwork for NeoForge 1.21.1

## English

### What is MiguelNetwork?

MiguelNetwork is a client-and-server networking mod for Minecraft Java Edition on NeoForge. It adds WebSocket
transport support while preserving the normal server list, status ping, login, Minecraft protocol, and gameplay.

It is intended for operators deploying Minecraft behind WebSocket-capable reverse proxies, managed ingress,
compatible CDNs, and hosting platforms that expose HTTP/WebSocket but not arbitrary TCP listeners. It can consolidate
public entry points while keeping Minecraft and ZstdNet backends private.

Operators may use routing, monitoring, rate limiting, origin protection, and DDoS mitigation supplied by their
selected gateway or CDN. MiguelNetwork does not itself provide a CDN or DDoS mitigation.

### Deployment principle

The default and recommended deployment is `STANDALONE`. MiguelNetwork's built-in gateway serves Discovery and plain
WebSocket on one public port, so no external gateway is required. Minecraft and ZstdNet listeners can remain private.

### Main features

- **Managed transport sidecar:** MiguelNetwork manages a pinned and verified wstunnel 10.7.1 process for WebSocket and
  long-lived transport.
- **One-port standalone gateway:** Discovery and WebSocket traffic share one public port by default.
- **Transparent Minecraft protocol:** Packets, logical handshake address, and gameplay remain unchanged; only the
  underlying socket path is adapted.
- **Automatic Discovery:** Clients obtain the endpoint, path, and detected backend from the logical server address.
- **Restricted server endpoint:** Generated rules allow forwarding only to the detected Minecraft and supported
  ZstdNet listeners—never SOCKS, an HTTP proxy, UDP, arbitrary targets, or reverse tunnels.
- **Ordinary-server compatibility:** When Discovery is unavailable, the optional legacy path can probe WebSocket and
  raw TCP. Set `legacyFallback = false` for strict Discovery-only behavior.
- **ZstdNet compatibility:** ZstdNet 1.4.7 and 1.4.8 are supported through an exact version gate.

### Supported environment

- MiguelNetwork 0.2.1 technical preview
- Minecraft Java Edition 1.21.1
- Java 21
- NeoForge compile baseline: 21.1.77
- Windows x86-64 client and dedicated server
- Linux x86-64 client and dedicated server
- The same Mod version must be installed on clients and the dedicated server
- Optional ZstdNet 1.4.7 or 1.4.8; when installed, use NeoForge 21.1.221 or newer

macOS, ARM, Bedrock, UDP Mod traffic, and SRV redirects are not currently supported. Validate this technical preview
against the target modpack and network before long-term production use.

### Players and clients

1. Install Minecraft 1.21.1, Java 21, and an appropriate NeoForge version.
2. Install the MiguelNetwork CurseForge file in the client's `mods` directory.
3. Install the same MiguelNetwork version on the target dedicated server.
4. If using ZstdNet, install version 1.4.7 or 1.4.8 and use NeoForge 21.1.221 or newer.
5. Enter the server owner's `hostname-or-IP:publicPort` in the normal Minecraft server list.

Default client configuration:

```toml
enabled = true
pathPrefix = "miguelnetwork-v1"
maxTunnelProcesses = 8
legacyFallback = true
```

`legacyFallback` allows this modded client to connect to ordinary TCP servers when Discovery is unavailable. It does
not allow an unmodded client to connect to a MiguelNetwork WebSocket endpoint.

### Server owners: standalone mode

`STANDALONE` is the default and recommended mode.

1. Install the same MiguelNetwork CurseForge file in the dedicated server's `mods` directory.
2. Keep Minecraft on a private address and separate backend port, for example:

   ```properties
   server-ip=127.0.0.1
   server-port=25567
   ```

3. Configure `config/miguelnetwork-server.toml`:

   ```toml
   enabled = true
   mode = "STANDALONE"
   bindHost = "0.0.0.0"
   publicPort = 35548
   pathPrefix = "miguelnetwork-v1"

   [discovery]
   enabled = true
   signResponses = false
   bindHost = "127.0.0.1"
   port = 25568
   advertisedTransport = "WS"
   advertisedHost = ""
   advertisedPort = 0
   configEpoch = 1
   validitySeconds = 120
   ```

4. Expose and forward only `publicPort` (TCP 35548 in this example). Keep Minecraft and ZstdNet backend ports private.
5. Start the server and confirm the log contains `standalone WS gateway is ready` and
   `Discovery and wstunnel share one port`.
6. Give players `public-host-or-IP:35548`.

Standalone deliberately uses plain WebSocket. This is the normal primary deployment, not a development-only fallback.

### Diagnostics and limitations

On a healthy standalone server, look for:

```text
MiguelNetwork standalone WS gateway is ready on <bind>:<publicPort>; Discovery and wstunnel share one port
```

On a client using Discovery, look for `selected Discovery route`. Messages containing `Discovery unavailable`,
`selected legacy ... route`, or `discovered vanilla TCP endpoint` indicate that the fallback path was entered.

Known technical-preview limitations:

- A forcibly terminated JVM or system can leave a sidecar process behind; normal shutdown cleans it up.
- Every modpack, multi-day runtime, forced network-loss recovery, SRV, and connection-modifying Mod remains unverified.
- Compatibility probing may add delay when Discovery is unavailable for an unknown server.
- WebSocket framing adds some overhead, and severe underlying packet loss can still cause jitter.

When reporting an issue, include exact Minecraft, NeoForge, MiguelNetwork, operating-system, and optional ZstdNet
versions with relevant redacted logs. Never publish private keys, tokens, or complete sensitive configuration.

### License and project status

MiguelNetwork is licensed under Apache-2.0. wstunnel remains under BSD-3-Clause. The distribution includes the required
license, copyright, pinned-version, hash, and provenance information.

This technical implementation does not guarantee CurseForge approval. Publication remains conditional on CurseForge
review and the maintainer's release decision.

---

## 中文

### MiguelNetwork 是什么？

MiguelNetwork 是一个同时运行在 Minecraft Java Edition 客户端和服务端的 NeoForge 网络 Mod。它为 Minecraft
增加 WebSocket 传输支持，同时保留正常的服务器列表、状态 Ping、登录、Minecraft 协议和游戏流程。

它适用于把 Minecraft 部署在支持 WebSocket 的反向代理、托管入口或兼容 CDN 之后，也适合只开放
HTTP/WebSocket、不能开放任意 TCP 监听端口的托管平台。管理员可以整合公网入口，并让 Minecraft 与
ZstdNet 后端保持在私有网络中。

管理员可以使用所选网关或 CDN 提供的路由、监控、限速、源站保护和 DDoS 缓解能力。MiguelNetwork 本身
不提供 CDN 或 DDoS 缓解。

### 部署原则

默认并推荐使用 `STANDALONE`。MiguelNetwork 的内置网关在一个公网端口上同时提供 Discovery 和明文
WebSocket，不需要外部网关，并可让 Minecraft 与 ZstdNet 监听器保持私有。

### 主要功能

- **受管理的传输 sidecar**：MiguelNetwork 管理固定并经过校验的 wstunnel 10.7.1 进程，负责 WebSocket
  与长连接传输。
- **单端口 standalone 网关**：默认由一个公网端口同时承载 Discovery 和 WebSocket 流量。
- **对 Minecraft 协议透明**：数据包、逻辑握手地址和游戏内容不变，只适配底层 Socket 路径。
- **自动 Discovery**：客户端从逻辑服务器地址获得端点、路径和自动检测出的后端。
- **受限的服务端入口**：生成的规则只允许转发到检测出的 Minecraft 与受支持 ZstdNet 监听器，不提供
  SOCKS、HTTP 代理、UDP、任意目标或反向隧道。
- **兼容普通服务器**：Discovery 不可用时，可选的旧版路径可以探测 WebSocket 和原始 TCP。需要严格
  Discovery-only 时可设置 `legacyFallback = false`。
- **ZstdNet 兼容**：通过精确版本门控支持 ZstdNet 1.4.7 和 1.4.8。

### 支持环境

- MiguelNetwork 0.2.1 技术预览版
- Minecraft Java Edition 1.21.1
- Java 21
- NeoForge 编译基线：21.1.77
- Windows x86-64 客户端和独立服务端
- Linux x86-64 客户端和独立服务端
- 客户端与独立服务端必须安装相同版本的 Mod
- 可选 ZstdNet 1.4.7 或 1.4.8；安装时需要 NeoForge 21.1.221 或更新版本

目前不支持 macOS、ARM、Bedrock、UDP Mod 流量和 SRV 重定向。正式长期使用前，应在目标整合包和网络环境
中验证这一技术预览版。

### 玩家与客户端

1. 安装 Minecraft 1.21.1、Java 21 和合适版本的 NeoForge。
2. 将 MiguelNetwork CurseForge 文件安装到客户端 `mods` 目录。
3. 在目标独立服务端安装相同版本的 MiguelNetwork。
4. 如需 ZstdNet，安装 1.4.7 或 1.4.8，并使用 NeoForge 21.1.221 或更新版本。
5. 在正常的 Minecraft 服务器列表中填写服主提供的 `域名或IP:publicPort`。

默认客户端配置：

```toml
enabled = true
pathPrefix = "miguelnetwork-v1"
maxTunnelProcesses = 8
legacyFallback = true
```

当 Discovery 不可用时，`legacyFallback` 允许安装本 Mod 的客户端连接普通 TCP 服务器。它不表示未安装
MiguelNetwork 的原版客户端可以连接 MiguelNetwork 的 WebSocket 入口。

### 服主：standalone 模式

`STANDALONE` 是默认并推荐的模式。

1. 将同一个 MiguelNetwork CurseForge 文件安装到独立服务端 `mods` 目录。
2. 让 Minecraft 使用私有地址和独立后端端口，例如：

   ```properties
   server-ip=127.0.0.1
   server-port=25567
   ```

3. 配置 `config/miguelnetwork-server.toml`：

   ```toml
   enabled = true
   mode = "STANDALONE"
   bindHost = "0.0.0.0"
   publicPort = 35548
   pathPrefix = "miguelnetwork-v1"

   [discovery]
   enabled = true
   signResponses = false
   bindHost = "127.0.0.1"
   port = 25568
   advertisedTransport = "WS"
   advertisedHost = ""
   advertisedPort = 0
   configEpoch = 1
   validitySeconds = 120
   ```

4. 只开放并转发 `publicPort`（示例为 TCP 35548），保持 Minecraft 和 ZstdNet 后端端口私有。
5. 启动服务端，确认日志包含 `standalone WS gateway is ready` 和
   `Discovery and wstunnel share one port`。
6. 将 `公网域名或IP:35548` 提供给玩家。

standalone 固定使用明文 WebSocket。这是正常的首选部署，不是仅供开发使用的降级方式。

### 诊断与限制

健康的 standalone 服务端日志应包含：

```text
MiguelNetwork standalone WS gateway is ready on <bind>:<publicPort>; Discovery and wstunnel share one port
```

使用 Discovery 的客户端应出现 `selected Discovery route`。`Discovery unavailable`、
`selected legacy ... route` 或 `discovered vanilla TCP endpoint` 表示进入了回退路径。

技术预览版的已知限制：

- JVM 或系统被强制终止时，sidecar 进程可能残留；正常退出会回收进程。
- 尚未验证所有整合包、多日运行、强制断网恢复、SRV 和全部连接栈 Mod。
- 首次访问 Discovery 不可用的未知服务器时，兼容探测可能带来额外等待。
- WebSocket 封装存在少量开销，严重的底层丢包仍可能引起抖动。

反馈问题时，请提供 Minecraft、NeoForge、MiguelNetwork、操作系统和可选 ZstdNet 的准确版本，以及相关的
脱敏日志。不得公开私钥、令牌或完整敏感配置。

### 许可证与项目状态

MiguelNetwork 使用 Apache-2.0 许可证。wstunnel 使用 BSD-3-Clause 许可证。发行物包含所需许可证、版权、
固定版本、哈希和来源信息。

这一技术实现不保证获得 CurseForge 批准。发布仍取决于 CurseForge 审核和维护者的发行决定。
