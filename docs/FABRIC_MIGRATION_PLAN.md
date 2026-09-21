# Fabric 1.20.1 / 1.21.1 迁移方案

更新：2026-09-17。状态：迁移设计；两个 Fabric 产物尚未实现或验证。

## 1. 决策与范围

优先补齐 Fabric 1.20.1，再完成 Fabric 1.21.1。先采用独立分支、独立 Gradle 构建，
复用现有传输实现和测试；两个 Fabric 版本验收后，再提取 Java 17 共享核心。
不将四平台仓库重构、构建工具统一或其他 Minecraft 版本扩展作为本次迁移的前置条件。

继续保持现有产品契约：

- `STANDALONE` 是默认及推荐部署，在 `server.publicPort` 上复用 Discovery 与明文 WS。
- `EXTERNAL_PROXY` 是可选部署，由外部网关终止 WSS；Mod 不管理证书。
- Discovery v1 优先；只有 Discovery 不可用且 `legacyFallback` 开启时才进入旧探测。
- 默认未签名 Discovery 与 WS 必须可用。签名、防降级和 TLS 都不新增为强制要求。
- ZstdNet 是可选依赖；保持压缩字节流串联，不复制压缩实现，不修改 ZstdNet JAR。
- 每个 Minecraft/Loader 组合独立发布 JAR，仍包含 wstunnel 10.7.1 Windows/Linux x86-64 二进制。
- 继续只自动启动 dedicated server 网关；集成服务器/LAN 发布不纳入首轮功能扩展。

## 2. 已核实的仓库基线

| 支持线 | 分支/提交 | Java / 构建 | 证据及边界 |
|---|---|---|---|
| NeoForge 1.21.1 | `main` / `85beadd` | Java 21、Gradle 8.9、ModDevGradle 1.0.21 | 编译基线 21.1.77；ZstdNet 1.4.7/1.4.8 要求 NeoForge >=21.1.221 |
| Forge 1.20.1 | `1.20.1` / `8269ee5` | Java 17、Gradle 8.14.5 | 编译基线 47.1.3；参见该分支 `docs/FORGE_VALIDATION.md` |

两个分支的 `mod_version` 均为 `0.2.0`。Forge 分支已有 Java 17 网关、128 连接准入、
双向工作线程容量控制、半关闭/退出清理和生产包验证，不应再从 main 重新实现这一轮降级。
其验证报告记录 35 个测试：34 通过、1 个可选真实端点测试跳过；本次没有重新运行这些测试。
Forge ZstdNet 1.4.8 的发布 JAR API 已在该分支核对，继承的 1.4.7 allowlist 尚未独立验证。

当前 main 工作区已有 `docs/DEVELOPMENT.md` 修改，以及未跟踪的 `docs/CROSS_VERSION_PLAN.md`
和 `docs/assets/`。本方案另建文件，不覆盖这些内容。旧跨版本计划仍将 Forge 描述为未来工作，
不能继续用它判断当前完成度；本方案以现有分支为迁移起点。

远程地址已核对为 `https://github.com/TheRealKamisama/miguelnetwork.git`。
本次未 fetch、创建分支、提交、打 tag、push 或发布。

## 3. ZstdNet Fabric 证据与待确认项

本地已有上游 `wish131400/zstdnet` 源码。该工作区不完整且索引有既有改动，
因此以下结论取自 Git 提交 `c9161fbe7c90cd33f50b73fb4e73d4e9b179dd47` 的对象，
不把工作树文件当作干净发布快照，也不修改该工作区。

该提交的两个 Fabric 模块均标记 ZstdNet `1.4.7`，使用官方 Mojang mappings：

| 项目 | Fabric 1.20.1 | Fabric 1.21.1 |
|---|---|---|
| 上游源代码中的 Loader | 0.16.10 | 0.16.10 |
| 上游源代码中的 Fabric API | 0.92.7+1.20.1 | 0.116.10+1.21.1 |
| 上游 Loom / wrapper | 1.7.4 / Gradle 8.8 | 1.7.4 / Gradle 8.8 |
| 目标 Java | 17 | 21 |
| `ConnectScreen.startConnecting` 差异 | 无 `TransferState` 参数 | 增加 `TransferState` 参数 |

这些是可追溯的技术验证候选组合，不是本项目已经验证的依赖下限，也不代表最新版本。
优先使用这组固定版本完成最小构建验证，再决定是否有必要升级；禁止动态版本及无理由追新。
Gradle 启动 JVM 与产物目标 Java 分开配置，依照插件要求选择启动 JVM，1.20.1 仍须在真实 Java 17 验收。

两个 Fabric 模块的 `coremod/ConnectScreenHooks.java` Git blob 完全一致：
`621ad54572d9bac1bf66c1fe731b063fc5bb6cd2`。源码可见：

- Fabric 使用 Mixin 调用 `ConnectScreenHooks.interceptConnect(ServerAddress, ServerData)`，并非 Forge coremod。
- `bypassing`、`currentProxy`、`LOCK` 仍存在；`takeProxy()` 将代理交回 ZstdNet 客户端生命周期。
- `LocalZstdNet.start(String,int,String,int,String,int,int,Mode)` 可承载现有代理串联。
- `ServerProxyConfigFile.readListenPort()` 仍提供服务端监听端口。
- 客户端 UI 会预先创建代理并设置 bypass；登录接管、取消连接和退出会影响代理释放。

因此应优先保留“拦截 ZstdNet 自身钩子”的适配策略，而非再抢占 Minecraft ConnectScreen 的注入顺序。
但源码相同不证明发布 JAR 兼容：**第一阶段必须取得两个 MC 版本对应的 Fabric 发布 JAR，记录下载来源、
版本、SHA-256、metadata、类和方法描述符。** 优先核验 1.4.8；仅对确有对应发布物且通过验证的
1.4.7/1.4.8 组合启用适配。若没有对应发布物，明确保留为未支持，不以其他 Loader 的同版本替代。

## 4. 分支与代码复用策略

建议实施时创建以下开发分支；此处没有执行创建：

1. `codex/fabric-1.20.1`：以 `1.20.1` 的 `8269ee5` 为起点，保留 Java 17 网关和测试，替换 Forge 集成层。
2. `codex/fabric-1.21.1`：从通过验收的 Fabric 1.20.1 实现派生，升级 MC/API/Java，并与 main 做行为对照。
   main 中已验证的 NeoForge 1.21.1 行为作为回归依据，不把 NeoForge 构建文件整批合并进 Fabric。
3. 已有 `main` 和 `1.20.1` 保持各自构建。共同行为修复按文件或提交同步，并在两条现有支持线回归。

首轮接受少量代码重复，以免同时承担平台接入和四平台构建重构风险。两个 Fabric 版本完成后，
提取普通 Java 17 library，逐个平台迁入；不引入 Architectury 作为必需运行依赖。
共享核心应包括 `core`、`discovery`、探测/信任存储、网关和端点解析。
`ClientTunnelManager`、`ServerTunnelController` 只有在注入配置、目录和运行端口后才能整体下沉；
目前仍引用 Loader 路径、平台入口 logger 或 `MinecraftServer`，不能直接标记为 Loader 无关。
Minecraft 类型、Mixin 和 ZstdNet 钩子适配保留在平台层。

未来共享核心随每个平台 JAR 打包，用户不另装 common JAR。各 Loader 可继续使用自己的 wrapper；
不要误以为 Gradle composite build 会分别使用被包含构建的 wrapper。

## 5. 具体迁移工作

| 现有位置/职责 | Fabric 方案 | 完成条件 |
|---|---|---|
| `MiguelNetwork` 入口与事件 | 拆分 `ModInitializer` / `ClientModInitializer`；注册 Fabric 生命周期事件 | dedicated server 不加载客户端类；启动/停止可重复且无孤儿进程 |
| 服务端生命周期 | `ServerLifecycleEvents.SERVER_STARTED` / `SERVER_STOPPING`，保留 dedicated 判断 | ZstdNet 自动端口调整完成后读取实际后端；核对其启动事件顺序，必要时增加有界就绪等待 |
| 客户端退出 | `ClientLifecycleEvents.CLIENT_STOPPING`；保留幂等 shutdown 兜底 | 退出清理 sidecar；断线/重连行为与现有隧道缓存契约一致 |
| `FMLPaths` | `FabricLoader.getInstance().getGameDir()` / `getConfigDir()` | native、trust store、生成配置与现有相对布局一致 |
| `ModList` | Fabric `getModContainer("zstdnet")` / metadata 版本 | 可选依赖缺失不报类加载错误；能力按平台、MC、ZstdNet 版本及 API 联合判定 |
| `ClientConfig` / `ServerConfig` | 保留 getter 接口和 TOML 格式，以明确固定版本的 Java 17 TOML 库实现 Fabric 配置适配 | 文件名、分组、默认值、范围、系统属性覆盖优先级与现状一致 |
| `ConnectionMixin` | 各 MC 版本核对精确 `Connection.connect` 描述符 | status/login 都只重定向一次；保留原始握手 host/port，内部 loopback 不再次套隧道 |
| `ZstdNetConnectScreenHooksMixin` | 保留可选第三方钩子；增加加载前 API/版本门控 | 不装 ZstdNet、未知版本和结构变化时均能安全加载；受支持组合生产环境确实注入 |
| 构建与 metadata | Loom、`fabric.mod.json`、客户端限定 Mixin、生产 `remapJar` | 发布的是 intermediary 生产包；无 Forge/NeoForge metadata 或类引用 |
| wstunnel 与分发验证 | 复用固定 manifest、下载缓存、哈希及许可证检查 | `verifyDistribution` 改为检查 `remapJar` 输出，`check/build` 包含该检查且无任务环 |

### 配置决定

Fabric 首轮不要求额外安装配置 Mod，不增加 GUI。推荐内嵌 TOML 库（例如核实后固定版本的
NightConfig core/toml），以 Loom 支持的嵌套依赖方式打包，同时检查许可证和依赖闭包。
不要把 ForgeConfigSpec 类或 Forge 配置运行时复制进 Fabric。

保留 `config/miguelnetwork-client.toml`、`config/miguelnetwork-server.toml` 及当前全部键；
新文件才生成默认模板。已有文件只读取，非法值提供明确诊断，不静默覆盖文件。
未设置的键使用现有默认值；保留系统属性覆盖行为。首轮明确配置在启动时读取，修改后重启，
不顺带实现热重载。必须覆盖配置缺省、边界值、错误枚举、路径及 `legacyFallback` 等行为测试。

### 映射与兼容门

Fabric 继续使用 Mojang mappings，可减少 Java 源码重命名；但运行时仍须转换为 intermediary。
1.20.1 的候选连接描述符来自已完成的 Forge 分支：

```text
connect(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;
```

需分别在两个 Fabric Minecraft 依赖中核对，不能用源码编译成功代替生产注入验证。
Minecraft 目标按 Loom/Mixin 正常重映射；第三方类名/方法名保持其实际名称。
现有 `remap = false` 的第三方钩子签名包含 Minecraft 类型，必须检查 remap 后的完整描述符，
不能把整个注入声明“不参与映射”等同于其中 Minecraft 类型无需重映射。
按选定 Loom 版本检查 Mixin/refmap 生成和引用，生产包不得遗留仅在开发环境可解析的目标。

建议以客户端专用 Mixin plugin 在应用可选钩子前核对版本及类结构（避免过早初始化目标类）。
单纯 `@Pseudo`、`require = 0` 或版本字符串 allowlist 不足以证明 API 可用。
通过能力核验后才启用 `zstdnet-stream`；API 缺失、未知版本或组合失败时不得继续宣称可用。
已选择 required 压缩路由后，代理创建/交接失败必须终止该连接，不能重新走未通告的直连路径。
不支持的压缩路由可跳过并选择 Discovery 明确通告的 raw route；没有兼容路由则失败。

## 6. 实施阶段与退出条件

| 阶段 | 工作 | 进入下一阶段的条件 |
|---|---|---|
| P0：发布物/API 调查 | 核验两个 Fabric 的 ZstdNet JAR、生命周期、映射和工具组合；固定依赖及校验和 | 得到可复现依赖清单，确认可选钩子和生产重映射路径；未核验版本不进入支持表 |
| P1：Fabric 1.20.1 基础接入 | Loom/metadata、配置、生命周期、普通 Connection 重定向；保留 Forge Java 17 网关 | Java 17 单元测试、生产包检查、无 ZstdNet 的真实客户端/专服 WS Discovery 通过 |
| P2：Fabric 1.20.1 ZstdNet | 可选钩子、能力门控、raw/压缩路由和 ProxyHandle 交接 | 已核验 ZstdNet 版本的列表加入/直接连接/取消/重连通过，无双重隧道或进程泄漏 |
| P3：Fabric 1.21.1 | 复用 Fabric 适配层，调整 MC/API/Java 21 和版本相关签名 | 独立生产 JAR 完成与 P1/P2 相同的验证，不能只测试开发 runClient |
| P4：回归与发布准备 | Windows/Linux 检查、四平台行为对照、文档/CI/分发元数据 | 所有必测项完成；记录各产物路径、SHA-256 和未覆盖项，才可标记兼容 |
| P5：后续维护整理 | 提取 Java 17 common、统一修复同步与构建约定 | 四平台回归保持通过；不阻塞 P4 的 Fabric 交付 |

若 P0 的工具组合失败，先记录首个仓库/插件/Java 错误，再调整具体依赖；
不升级现有两条支持线来解决 Fabric 构建问题。每个 Fabric 构建使用检查入库且校验和固定的 wrapper。

## 7. 验收矩阵

以下矩阵对 Fabric 1.20.1/Java 17 和 Fabric 1.21.1/Java 21 各自执行：

| 维度 | 必测内容 |
|---|---|
| 无 ZstdNet | 默认 WS + 未签名 Discovery；status ping、列表加入、直接连接、取消、重连；裸 TCP 服务回退 |
| ZstdNet 组合 | 客户端/服务端均安装、仅服务端安装、仅客户端安装；每个获准版本分别测；确认实际 raw/压缩路由 |
| 错误兼容 | 未知版本、缺失方法/字段、钩子未生效、代理创建/交接失败；无兼容 required route 必须终止 |
| Discovery | 有效 manifest、无效 manifest、未知 required filter；Discovery 不可用时 `legacyFallback` 开/关 |
| 可选控制 | EXTERNAL_PROXY + WSS；证书成功/失败；启用签名验证/防降级后的原有语义 |
| 并发/生命周期 | 双客户端双向大流量、半关闭、128 连接准入与槽位恢复、端口冲突、断网、服务器退出 |
| 操作系统 | Windows/Linux x86-64 的 native 提取/哈希/启动/退出、权限和路径空格 |
| 发布包 | remapJar 真实安装启动；纯服务端无客户端类加载；MC 精确版本、Java major、Mixin、嵌套依赖及许可证 |
| 现有支持线 | Forge 1.20.1 和 NeoForge 1.21.1 的原有构建与传输回归；NeoForge 的 ZstdNet 行使用 >=21.1.221 |

成功路径应出现 `standalone WS gateway is ready`、`Discovery and wstunnel share one port`，
客户端出现 `selected Discovery route`。`Discovery unavailable`、`selected legacy ... route`
或 `discovered vanilla TCP endpoint` 只能证明进入回退，不能作为 Discovery 成功证据。
压缩组合还需记录实际 route/filter 和代理接管，不以“成功进服”代替确认流量走过 WS。

同一 MC 版本跨 Loader 的传输互操作可用无额外内容 Mod 的兼容客户端/服务器单独验证；
不承诺不同 Minecraft 协议版本互连，也不保证任意 Forge/Fabric 整合包互通。

## 8. CI、产物和发布边界

- 预期产物：`build/libs/miguelnetwork-fabric-1.20.1-<mod_version>.jar`、
  `build/libs/miguelnetwork-fabric-1.21.1-<mod_version>.jar`，分别由对应构建产生。
- `fabric.mod.json` 使用精确 MC 版本、Java >=17 / >=21，以及经过验证的 Loader/API 下限；
  `environment` 为 `*`，客户端入口和 Mixin 单独限定。ZstdNet 不设为必需依赖。
- `mod_version` 继续来自对应分支根 `gradle.properties`；本轮不改版本。
- CI 构建并检查生产 remap JAR；测试报告、SHA-256、产物标识含 Loader/MC，避免混包。
  Java 17 产物 major <=61，Java 21 产物 major <=65；内嵌依赖也检查相应运行兼容性。
- 当前发布工作流硬编码 MC/Loader 并假定只有一个 JAR，不能原样复制为四产物发布。
  发布准备时把目标与 JAR 建立显式映射，校验 CurseForge 的 Fabric、MC、Client/Server 元数据。
  Git tag 在仓库内全局唯一，不能让各分支重复用同一 tag 覆盖不同资产。
  首轮构建 CI 不触发发布；后续单独设计同版本多资产或明确目标的发布任务。
- 依赖与构建缓存纳入 `.gitignore`（包括 Loom 新增缓存）；不提交日志、信任库、生成配置、
  第三方发布 JAR 或用户实例。已有 native 来源、哈希和许可证规则继续执行。
- 提交、tag、push、发布仍需用户明确授权对应操作；本设计不执行这些操作。

## 9. 本次验证记录

本次执行 Git/源码/构建脚本静态检查和现有 JAR SHA-256 读取；未执行 Gradle、Fabric 启动或游戏连接。
仅新增本设计文档，未修改程序或既有规划。现存产物作为基线记录，不作为 Fabric 支持证据：

| 分支 | 仓库相对产物路径 | 本次读取的 SHA-256 |
|---|---|---|
| main | `build/libs/miguelnetwork-neoforge-1.21.1-0.2.0.jar` | `A4FE4133E04E5815040B68F500E25C5BBBC5A9BA928DA3763727B9E501837796` |
| 1.20.1 | `build/libs/miguelnetwork-forge-1.20.1-0.2.0.jar` | `8AE534F5195AD592247B722EA3438DFA29450408DDBBE085838BF55FAEF90E36` |

迁移实施完成后，另写 Fabric 验证报告，记录真实工具版本、ZstdNet 文件来源/哈希、
执行命令、通过/跳过项、运行系统和最终产物 SHA-256。
