# MiguelNetwork — CurseForge Description / CurseForge 项目介绍

> **Short description / 简短介绍**
> Carry Minecraft TCP over WebSocket with a bundled Rust wstunnel sidecar and a one-port standalone gateway. Mitigates raw-TCP QoS/blocking; optional proxy-terminated WSS; ZstdNet 1.4.8 Forge.
> 通过内置 Rust wstunnel sidecar 和单端口 standalone 网关承载 Minecraft TCP，缓解原生 TCP QoS/阻断；可选反代 WSS，兼容 ZstdNet 1.4.8 Forge。

---

## 中文介绍

### MiguelNetwork 是什么？

MiguelNetwork 是一个同时运行在 Minecraft Java Edition 客户端和服务端的 Forge 网络 Mod。它把原始
Minecraft TCP 字节流承载在 WebSocket 上，同时保留玩家熟悉的服务器列表、状态 Ping、登录和游戏流程。

玩家仍然填写普通的服务器域名和端口。Minecraft 的握手地址、登录加密、数据包格式和游戏内容均不改变；
MiguelNetwork 只替换底层网络路径。与在 Java 网络栈中重新实现 WebSocket 的方案不同，本项目由 Mod 管理
随 JAR 分发的 **wstunnel Rust sidecar 子进程**，实际隧道和长连接交给成熟的 wstunnel 实现。

### 核心价值：缓解三网公网 IP 的 TCP QoS 与连接阻断

近期部分中国电信、中国移动和中国联通公网 IP 用户遇到针对入站长连接或原生 Minecraft TCP 流量的 QoS、
连接重置、间歇性阻断等问题，夜间高峰尤其明显。MiguelNetwork 把原生 Minecraft TCP 的外层承载转换为
WebSocket，使连接不再直接表现为原生 Minecraft TCP。

对于**只针对原生 TCP 特征、端口或连接形态**实施的限制，这条路径可以绕过或显著缓解 QoS/阻断，减少购买
商业游戏中转、专线或高价托管的必要，从而降低自建 Minecraft 服务器的成本，并提高晚高峰时段的连接成功率
与可用性。

实际效果取决于当地运营商策略、网络设备和线路质量。MiguelNetwork 不是通用 VPN，无法绕过所有网络策略，
也不会改善底层物理链路丢包。请遵守当地法律、运营商条款和所使用代理服务的规则。

### 部署原则：standalone 优先，加密可选

MiguelNetwork **不强制传输层加密**。默认并推荐的部署方式是 `STANDALONE`：Mod 自带的网关在一个公网端口
上同时处理 Discovery 和明文 WS，无需证书、Nginx 或其他外部组件。Minecraft 自身的登录加密仍然保持不变；
这里的“不加密”仅指 WebSocket 外层没有 TLS。

如果服主希望使用 TLS/WSS，可选择第二种 `EXTERNAL_PROXY` 模式，由 Nginx、Caddy、HAProxy 或其他网关
持有证书并终止 WSS。外部网关到 MiguelNetwork 私有上游的连接仍是 WS。MiguelNetwork 本身不申请、管理
或强制要求证书。

### 主要功能

- **成熟 Rust sidecar 路线**：使用现有 wstunnel 项目处理 WebSocket 与长连接。上游项目的长期运行能力已有
  真实使用历史；MiguelNetwork 固定并集成经过验证的版本，避免重新编写一套年轻的传输协议栈。
- **开箱即用的 standalone 网关**：默认把 Discovery 和 wstunnel 复用到一个公网 WS 端口，服主只需开放
  一个额外 TCP 端口，不需要反向代理和证书。
- **无需单独安装 wstunnel**：Windows x64 与 Linux x64 官方二进制随 Mod JAR 分发，运行时自动解压、启动、
  监控和关闭。
- **供应链校验**：构建时校验上游归档及二进制 SHA-256；最终 JAR 再次检查固定哈希、许可证和来源文件。
- **对 Minecraft 透明**：不修改 Minecraft 数据包、登录加密或游戏内容，只把底层 Socket 指向本地隧道；
  原始逻辑服务器地址仍用于 Minecraft 握手。
- **自动 Discovery**：客户端先在玩家填写的逻辑地址上请求 Discovery，自动获得 WS/WSS 端点、路径和内部
  目标地址。standalone 网关在同一公网端口直接提供 Discovery，玩家无需逐服配置目标端口或传输类型。
- **加密与签名均为可选能力**：Discovery 签名、身份固定、WSS 和降级保护可由管理员按需开启，默认不强制。
  选择 WSS 时，客户端仍会正常校验证书链与主机名。
- **受限的服务端入口**：自动生成 wstunnel restriction，仅允许 TCP 转发到实际检测出的 Minecraft 和
  ZstdNet 本地监听器，不开放 SOCKS、HTTP 代理、UDP、任意目标或反向隧道。
- **兼容普通服务器**：Discovery 不可用时，默认可以按 WSS、WS、原始 TCP 的顺序执行旧版探测，因此安装
  本 Mod 后仍可连接普通 Minecraft 服务器。需要严格 Discovery-only 时可关闭 `legacyFallback`。
- **ZstdNet 1.4.8 Forge 兼容**：适配器会自动发现 ZstdNet 监听端口，并优先路由已经压缩的字节流；不复制、
  修改或重新实现 Zstd 压缩。客户端没有 ZstdNet 时仍可选择原始 Minecraft 路由。

### 技术路线

默认 standalone 数据路径：

```text
Minecraft 客户端
  -> 127.0.0.1:<动态端口>
  -> MiguelNetwork 管理的 wstunnel client
  == 明文 WS ==>
  -> MiguelNetwork 内置 standalone 网关（公网端口）
  -> 动态回环端口上的 wstunnel server
  -> Minecraft 本地监听器
```

使用外部 WSS 网关时：

```text
Minecraft 客户端
  -> wstunnel client
  == WSS ==>
  -> Nginx/Caddy/HAProxy（TLS 终止）
  == 私有 WS ==>
  -> wstunnel server
  -> Minecraft 本地监听器
```

启用 ZstdNet 1.4.8 Forge 时，登录流量为：

```text
Minecraft 客户端
  -> ZstdNet 客户端回环代理
  -> MiguelNetwork/wstunnel client
  == WS 或 WSS ==>
  -> wstunnel server
  -> ZstdNet 服务端监听器
  -> Minecraft 服务端
```

MiguelNetwork 只传输已经压缩的 TCP 字节流。Discovery 会优先发布带 `zstdnet-stream` 过滤器的路由，同时
保留原始 Minecraft 路由。

### 支持环境

- MiguelNetwork 0.2.0 技术预览版
- Minecraft Java Edition 1.20.1
- Java 17
- 编译基线：Forge 47.1.3
- Windows x64 客户端/服务端
- Linux x64 客户端/服务端
- 同一个 JAR 用于客户端和独立服务端
- 可选兼容：ZstdNet **1.4.8 Forge 1.20.1 构建**（已核对反射 API）
- 继承的 1.4.7 版本白名单尚未在 Forge 构建上单独核实；不要安装 NeoForge 版 JAR

macOS、ARM、Bedrock、UDP Mod 流量和 SRV 重定向目前不受支持。0.2.0 仍是技术预览版，正式投入长期运行前
应在目标整合包和网络环境中验证。

### 使用说明

#### 玩家/客户端

1. 安装 Minecraft 1.20.1、Java 17 和合适版本的 Forge。
2. 将 MiguelNetwork JAR 放入客户端 `mods` 目录；无需单独安装 wstunnel，也没有必需的前置 Mod。
3. 如需 ZstdNet，安装 Forge 1.20.1 版 1.4.8（CurseForge 文件 8752125）。
4. 正常启动游戏，在服务器列表中填写服主提供的 `域名或IP:publicPort`。
5. 客户端会自动请求 Discovery、选择兼容路由并启动本地 sidecar。

默认客户端配置通常无需修改：

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

`legacyFallback` 让安装了本 Mod 的客户端仍能连接普通 TCP 服务器。它不表示未安装 MiguelNetwork 的原版
客户端可以连接 MiguelNetwork 的 WS/WSS 入口。

#### 服主：方案一，standalone（默认并推荐）

1. 将同一个 MiguelNetwork JAR 放入独立服务端 `mods` 目录。可选安装 ZstdNet 1.4.8 Forge。
2. 让 Minecraft 后端使用私有地址和独立端口，例如：

   ```properties
   server-ip=127.0.0.1
   server-port=25567
   ```

3. 首次启动会生成 `config/miguelnetwork-server.toml`。默认 standalone 配置如下：

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

4. 在防火墙/NAT 中只开放并转发 `publicPort`（示例为 TCP 35548）。不要暴露 Minecraft 或 ZstdNet 后端端口。
5. 启动服务器，确认日志包含 `standalone WS gateway is ready` 和
   `Discovery and wstunnel share one port`。
6. 将 `公网域名或IP:35548` 提供给玩家。客户端不需要知道 Minecraft/ZstdNet 的内部端口。

standalone 固定使用明文 WS，并且不创建或管理证书。这是设计中的正常首选路径，不是仅供测试的降级模式。

#### 服主：方案二，外部反代 + WSS（可选）

已有反向代理或确实需要 TLS 时，将模式改为 `EXTERNAL_PROXY`：

```toml
enabled = true
mode = "EXTERNAL_PROXY"
bindHost = "127.0.0.1"
publicPort = 35549
pathPrefix = "miguelnetwork-v1"

[discovery]
enabled = true
signResponses = false
bindHost = "127.0.0.1"
port = 25568
advertisedTransport = "WSS"
advertisedHost = "play.example.net"
advertisedPort = 35548
configEpoch = 1
validitySeconds = 120
```

由 Nginx/Caddy/HAProxy 在公网 `play.example.net:35548` 终止 TLS，并完成两条内部路由：

- `/.well-known/miguelnetwork/v1` 转发到 Discovery HTTP `127.0.0.1:25568`；
- `/miguelnetwork-v1/` 转发到 wstunnel WS `127.0.0.1:35549`。

反向代理必须保留原始 `Host` 并正确传递 `Upgrade`/`Connection` 头。证书完全由外部反代管理；
MiguelNetwork 的私有上游保持 WS。

完整配置、日志检查和协议说明见项目仓库：

- `docs/USER_GUIDE.md`
- `docs/CONFIGURATION.md`
- `docs/DISCOVERY_PROTOCOL.md`
- `docs/ZSTDNET_COMPATIBILITY.md`

### 可选安全控制

如需验证 Discovery 身份，可在服务端启用 `signResponses = true`，并在客户端启用
`verifyDiscoverySignatures = true`。如需强制已知 WSS 端点不得降级，还可启用
`enforceWssDowngradeProtection = true`。

这些是可选的管理员策略，不是 MiguelNetwork 的默认运行要求。仅当使用签名 Discovery 时才会生成
`config/miguelnetwork/generated/discovery-identity.key`；此时应备份私钥，且绝不能上传或分享。

### 已验证内容与技术预览限制

以下是原 NeoForge 1.21.1 分支的历史验证，不代表本分支的 Forge 运行验收。Forge 构建记录见 [FORGE_VALIDATION.md](FORGE_VALIDATION.md)。

原分支核心链路已完成 Windows 客户端到 Linux 服务端的跨主机 WS/WSS 测试，包括状态 Ping、账户认证、进入世界、
约 37 分钟实机游戏和连续 50 次公网状态连接。standalone 网关已在 ATM10 环境中通过真实日志验证：公网网关、
回环 wstunnel、ZstdNet 1.4.7 和 Minecraft 后端均按预期组成，Discovery 分别选择了原始状态路由和 ZstdNet
登录路由。ZstdNet 1.4.8 的相同 API 兼容性已经核实，并已纳入精确版本门控和单元测试。

已知限制包括：

- JVM/系统被强制终止时，sidecar 可能残留；正常退出会回收进程。
- 尚未完成所有整合包、多日运行、强制断网恢复、SRV 和全部连接栈 Mod 的验证。
- 首次访问 Discovery 不可用的未知服务器时，兼容探测可能带来额外等待。
- WS/WSS 包装有少量额外开销；严重丢包线路仍可能出现抖动。

问题反馈请附 Minecraft、Forge、MiguelNetwork、操作系统和（如有）ZstdNet 的准确版本，以及已脱敏日志。
请勿公开 Discovery 私钥、TLS 私钥、令牌或完整敏感配置。

### 许可证与第三方组件

MiguelNetwork 使用 Apache-2.0 许可证。随包分发的 wstunnel 使用 BSD-3-Clause 许可证；相应许可证、版权
声明、固定版本、哈希和来源信息均包含在发布 JAR 中。

---

## English

### What is MiguelNetwork?

MiguelNetwork is a client-and-server networking mod for Minecraft Java Edition on Forge. It carries the original
Minecraft TCP byte stream over WebSocket while preserving the normal server list, status ping, login, and gameplay.

Players still enter an ordinary hostname and port. Minecraft's logical handshake address, login encryption, packet
format, and gameplay remain unchanged; only the underlying network path is redirected. Instead of reimplementing
WebSocket in Java, MiguelNetwork manages a **bundled Rust wstunnel sidecar** and delegates tunnelling and long-lived
connections to the mature wstunnel project.

### Primary use case: mitigating ISP QoS and raw-TCP blocking

Some public-IP users of China Telecom, China Mobile, and China Unicom have experienced QoS, connection resets, or
intermittent blocking that targets inbound long-lived connections or native Minecraft TCP, especially during evening
peak hours. MiguelNetwork changes the outer transport from native Minecraft TCP to WebSocket.

When restrictions target native-TCP characteristics, ports, or connection patterns, this route can bypass or
substantially mitigate them. It may reduce the need for paid game relays, dedicated lines, or expensive hosting,
lowering the cost of self-hosting while improving peak-hour connection success and availability.

Results depend on local ISP policy, network equipment, and route quality. MiguelNetwork is not a general VPN, cannot
bypass every policy, and cannot repair packet loss on the underlying link. Follow applicable laws and service terms.

### Deployment principle: standalone first, encryption optional

MiguelNetwork **does not require transport encryption**. The default and recommended deployment is `STANDALONE`: the
built-in gateway serves Discovery and plain WS on one public port, with no certificate, Nginx, or other external
component required. Minecraft's own login encryption is unchanged; “unencrypted” here only means that the outer
WebSocket transport has no TLS.

Operators who want TLS/WSS can choose the secondary `EXTERNAL_PROXY` mode. Nginx, Caddy, HAProxy, or another gateway
owns the certificate and terminates WSS; its private upstream connection to MiguelNetwork remains WS. MiguelNetwork
does not obtain, manage, or require certificates.

### Main features

- **Mature Rust sidecar:** WebSocket and long-lived transport are handled by the existing wstunnel project, whose
  long-running behavior has real-world history. MiguelNetwork pins and integrates a verified version.
- **One-port standalone gateway:** Discovery and wstunnel share one public plain-WS port by default. No reverse proxy or
  certificate setup is required.
- **No separate wstunnel installation:** Official Windows x64 and Linux x64 executables are bundled, verified, extracted,
  started, monitored, and stopped automatically.
- **Transparent to Minecraft:** Packets, login encryption, and gameplay are unchanged. Only the low-level socket target
  is redirected, while the original logical hostname remains in the Minecraft handshake.
- **Automatic Discovery:** The client obtains the WS/WSS endpoint, path, and detected backend address from the logical
  server address. Players do not configure target ports or transport types per server.
- **Optional security controls:** Discovery signatures, identity pinning, WSS, and downgrade protection are available
  but disabled by default. Normal certificate validation still applies whenever WSS is selected.
- **Restricted server endpoint:** Generated wstunnel rules allow TCP forwarding only to the detected Minecraft and
  supported ZstdNet listeners—never SOCKS, an HTTP proxy, UDP, arbitrary targets, or reverse tunnels.
- **Ordinary-server compatibility:** When Discovery is unavailable, the default legacy path may probe WSS, WS, then raw
  TCP. Set `legacyFallback = false` for strict Discovery-only behavior.
- **ZstdNet 1.4.8 Forge compatibility:** MiguelNetwork auto-detects the ZstdNet listener and carries its already-compressed
  stream without copying or modifying the Zstd implementation. A raw Minecraft route remains available.

### Technical architecture

Recommended standalone path:

```text
Minecraft client
  -> 127.0.0.1:<dynamic port>
  -> MiguelNetwork-managed wstunnel client
  == plain WS ==>
  -> built-in standalone gateway on the public port
  -> wstunnel server on a dynamic loopback port
  -> private Minecraft listener
```

Optional external-WSS path:

```text
Minecraft client
  -> wstunnel client
  == WSS ==>
  -> Nginx/Caddy/HAProxy (TLS termination)
  == private WS ==>
  -> wstunnel server
  -> private Minecraft listener
```

With ZstdNet 1.4.8 for Forge, login traffic becomes:

```text
Minecraft client
  -> ZstdNet client loopback proxy
  -> MiguelNetwork/wstunnel client
  == WS or WSS ==>
  -> wstunnel server
  -> ZstdNet server listener
  -> Minecraft server
```

MiguelNetwork sees only the already-compressed TCP stream. Discovery prioritizes the `zstdnet-stream` route and also
publishes a raw Minecraft route.

### Supported environment

- MiguelNetwork 0.2.0 technical preview
- Minecraft Java Edition 1.20.1
- Java 17
- Compile baseline: Forge 47.1.3
- Windows x64 client/server
- Linux x64 client/server
- The same JAR is used on the client and dedicated server
- Optional compatibility: ZstdNet **1.4.8 for Forge 1.20.1** (reflective API checked)
- The inherited 1.4.7 allowlist entry has not been independently verified on Forge; do not install a NeoForge JAR

macOS, ARM, Bedrock, UDP mod traffic, and SRV redirects are not supported. Validate this technical preview against the
target modpack and network before long-term production use.

### How to use

#### Players / clients

1. Install Minecraft 1.20.1, Java 17, and a suitable Forge version.
2. Put the MiguelNetwork JAR in the client's `mods` directory. No separate wstunnel installation is needed.
3. To use ZstdNet, install version 1.4.8 for Forge 1.20.1 (CurseForge file 8752125).
4. Enter the server owner's `hostname-or-IP:publicPort` in the normal server list.
5. MiguelNetwork automatically requests Discovery, selects a route, and starts its local sidecar.

Default client configuration normally needs no changes:

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

`legacyFallback` lets this modded client join ordinary TCP servers. It does not let an unmodded client connect to a
MiguelNetwork WS/WSS endpoint.

#### Server owners: option 1, standalone (default and recommended)

1. Put the same MiguelNetwork JAR in the dedicated server's `mods` directory. ZstdNet 1.4.8 Forge is optional.
2. Keep Minecraft on a private address and a separate port, for example:

   ```properties
   server-ip=127.0.0.1
   server-port=25567
   ```

3. The generated default `config/miguelnetwork-server.toml` is:

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
5. Start the server and confirm `standalone WS gateway is ready` and
   `Discovery and wstunnel share one port` in the log.
6. Give players `public-host-or-IP:35548`. Clients do not need the backend ports.

Standalone deliberately uses plain WS and creates no certificates. This is the normal recommended path, not a
development-only fallback.

#### Server owners: option 2, external proxy + WSS (optional)

When an existing reverse proxy or TLS is desired, use `EXTERNAL_PROXY`:

```toml
enabled = true
mode = "EXTERNAL_PROXY"
bindHost = "127.0.0.1"
publicPort = 35549
pathPrefix = "miguelnetwork-v1"

[discovery]
enabled = true
signResponses = false
bindHost = "127.0.0.1"
port = 25568
advertisedTransport = "WSS"
advertisedHost = "play.example.net"
advertisedPort = 35548
configEpoch = 1
validitySeconds = 120
```

Terminate TLS at public `play.example.net:35548` using Nginx/Caddy/HAProxy, then route:

- `/.well-known/miguelnetwork/v1` to Discovery HTTP at `127.0.0.1:25568`;
- `/miguelnetwork-v1/` to wstunnel WS at `127.0.0.1:35549`.

Preserve the original `Host` and forward the `Upgrade`/`Connection` headers. The external gateway owns all certificate
management; MiguelNetwork's private upstream remains WS.

See the repository documentation for complete configuration and diagnostics:

- `docs/USER_GUIDE.md`
- `docs/CONFIGURATION.md`
- `docs/DISCOVERY_PROTOCOL.md`
- `docs/ZSTDNET_COMPATIBILITY.md`

### Optional security controls

To authenticate Discovery, enable `signResponses = true` on the server together with
`verifyDiscoverySignatures = true` on clients. To prevent a known WSS endpoint from later downgrading, optionally enable
`enforceWssDowngradeProtection = true`.

These are operator-selected controls, not default requirements. A
`config/miguelnetwork/generated/discovery-identity.key` is generated only for signed Discovery; back it up and never
upload or share it.

### Validation and technical-preview limitations

The following records historical NeoForge 1.21.1 validation, not Forge runtime acceptance. See [FORGE_VALIDATION.md](FORGE_VALIDATION.md) for this branch.

The original core path has been tested from Windows clients to Linux servers over WS/WSS, including status pings, account login,
world entry, an approximately 37-minute gameplay session, and 50 sequential public status connections. The standalone
gateway has also been verified in ATM10 using matching client/server logs: public gateway, loopback wstunnel, ZstdNet
1.4.7, and the Minecraft backend composed as designed. Discovery selected the raw status route and the ZstdNet login
route. ZstdNet 1.4.8's matching API compatibility was verified and added to the exact-version gate and unit tests.

Known limitations include:

- A forcibly terminated JVM/system can leave a sidecar behind; normal shutdown cleans it up.
- Every modpack, multi-day runtime, forced network-loss recovery, SRV, and all connection-modifying mods remain unverified.
- Compatibility probing can add delay when Discovery is unavailable for a previously unknown server.
- WS/WSS framing adds a small overhead, and a severely lossy underlying route can still cause jitter.

When reporting an issue, include exact Minecraft, Forge, MiguelNetwork, operating-system, and—if applicable—ZstdNet
versions plus relevant redacted logs. Never publish private keys, tokens, or complete sensitive configuration.

### License and third-party software

MiguelNetwork is licensed under Apache-2.0. The bundled wstunnel executable remains under BSD-3-Clause; its license,
copyright notice, pinned version, hashes, and provenance are included in the release JAR.
