# MiguelNetwork for Cleanroom 1.12.2

## English

MiguelNetwork 0.2.0 adds WebSocket transport to Minecraft Java Edition while preserving the normal server list, status, login and gameplay. Install the same version on the client and dedicated server.

### Requirements and installation

- Minecraft 1.12.2; Cleanroom 0.6.13-alpha, Java 25.
- Windows x86-64 or Linux x86-64. Other architectures are unsupported. Runtime validation is platform-specific; see the project's validation documentation.
- Install the matching MiguelNetwork CurseForge JAR into `mods`. Do not use a development JAR or a different loader's file.
- ZstdNet is optional. Use only the version/API combination documented in this branch's compatibility guide.

### Recommended deployment

`STANDALONE` is the default. The built-in gateway serves Discovery and plain WebSocket on one public port without an external gateway. Keep the Minecraft backend private. Configure `publicPort` and the advertised address in `config/miguelnetwork-server.toml` according to the user guide.

Players enter the server owner's `hostname:publicPort` in the ordinary Minecraft server list. Discovery selects an advertised route. Set `legacyFallback = false` to require Discovery instead of legacy probing. Unknown required filters are rejected.

The managed wstunnel 10.7.1 sidecar carries the connection. The gateway restricts forwarding to the configured Minecraft and supported ZstdNet backend. It is not a general proxy or a DDoS protection service.

### Diagnostics

The server logs `standalone WS gateway is ready` and `Discovery and wstunnel share one port`. A client using Discovery logs `selected Discovery route`. Consult the branch's user guide for configuration and compatibility details.

## 中文

MiguelNetwork 0.2.0 为 Minecraft Java 版提供 WebSocket 传输，保留正常的服务器列表、状态查询、登录和游戏流程。客户端与专用服务器安装同一版本。

### 环境和安装

- Minecraft 1.12.2；Cleanroom 0.6.13-alpha, Java 25。
- 支持 Windows x86-64、Linux x86-64；实测范围以本分支验证文档为准。
- 将对应加载器的 CurseForge JAR 放入 `mods`，不要使用开发包或其他加载器的文件。
- ZstdNet 为可选组件，仅使用本分支兼容性文档列明的版本和 API 组合。

### 推荐部署

默认采用 `STANDALONE`，内置网关在一个公网端口提供 Discovery 和普通 WebSocket，无需外部网关。Minecraft 后端应保持私有。按用户指南设置 `config/miguelnetwork-server.toml` 的 `publicPort` 和公布地址。

玩家在正常服务器列表输入服主提供的 `主机名:公网端口`。Discovery 选择已公布路由；如要求必须使用 Discovery，将 `legacyFallback` 设为 `false`。未知必需过滤器会被拒绝。

MiguelNetwork 管理固定版本的 wstunnel 10.7.1 进程，仅允许转发至配置的 Minecraft 和受支持的 ZstdNet 后端，不提供通用代理或 DDoS 防护服务。

服务端就绪日志包括 `standalone WS gateway is ready`、`Discovery and wstunnel share one port`；客户端使用 Discovery 时出现 `selected Discovery route`。配置和兼容性以本分支用户指南为准。
