# Minecraft 1.12.2 / Cleanroom / Java 21 迁移可行性评估

核对日期：2026-09-17。状态：源码与官方资料评估，未实现或运行 Cleanroom 移植版。

## 结论

**技术上可行，建议先做独立平台原型。Java 21 路线需要固定旧版 Cleanroom，不能使用当前最新版。**

本次找到一套明确匹配的官方历史模板：

- Minecraft **1.12.2**，Cleanroom **0.4.4-alpha**，Java **21**。
- CleanroomModTemplate 的 `mixin` 历史提交
  `df9ead105ca1aaf4fef10bcf6d2e50d2d160e5b0`。
- Unimined **1.4.14-kappa**、Gradle wrapper **9.3.1**、MCP **stable 39-1.12**。
- 模板显式设置 Java 21 toolchain、编译目标和测试 JVM，并使用 `remapJar` / `remapShadowJar`。

这是**已核对的开发候选组合**，不是 MiguelNetwork 已通过的构建或运行组合。
Cleanroom 0.4.4-alpha 的 README 明确写着 `1.12.2 on Java 21`；0.5.0-alpha 发布说明
明确记录 `Migrate to Java25 + Full patch cleanup`。当前最新发布为 **0.6.13-alpha**，
当前 README 为 **Java 25+**。不能只把最新版模板的 toolchain 改为 21 来获得 Java 21 支持。

| 目标组合 | 评估 | 主要取舍 |
|---|---|---|
| 1.12.2 + Cleanroom 0.4.4-alpha + Java 21 | 有条件可行，符合本次指定目标 | 固定 Loader、模板及兼容 Mod 版本；后续修复覆盖须逐项评估 |
| 1.12.2 + 当前 Cleanroom + Java 25 | 可作为后续路线 | 跟进上游，但改变本次指定的运行 JVM；需要独立验证 |
| 1.12.2 + 普通 Forge + Java 8 | 不属于本次迁移范围 | 需要额外语言/API 降级和旧工具链适配，成本明显增加 |
| 直接加载现有 NeoForge/Forge 1.20.1 JAR | 不可作为移植方案 | Minecraft 类、Loader API、Mixin 目标及 metadata 均不匹配 |

推荐先证明 Java 21 原型可用，再决定是否长期维护冻结的 Loader 基线。
若目标是持续跟进 Cleanroom 最新版，应单独选择 Java 25 路线，而不是声明 Java 21 也受支持。

## Git 与本地工作区现状

- 当前主工作树：`main`，HEAD `85beadd`，`mod_version=0.2.0`，NeoForge 21.1.77，
  Minecraft 1.21.1，Java 21，ModDevGradle 1.0.21，wrapper 8.9。
- Git remote 已核对为 `TheRealKamisama/miguelnetwork` GitHub 项目；本次没有远端写入。
- 阅读前已有 `docs/DEVELOPMENT.md` 修改，以及未跟踪的 `docs/CROSS_VERSION_PLAN.md`
  和 `docs/assets/`；本次保留这些内容，仅新增本评估。
- 本地另有 Forge 1.20.1 修复工作树，HEAD **8269ee5**，工作树干净。
  其 `docs/FORGE_VALIDATION.md` 记录 Java 17 构建通过、35 项测试中 34 通过、1 项可选测试跳过，
  并验证生产映射、Mixin 注册、refmap、字节码与内嵌二进制。该记录不代表本轮重跑。
- 因而 2026-09-12 跨版本计划中“Forge 尚未实施”的描述已经落后于本地工作区，
  不能把主工作树中的旧计划当作所有分支的当前状态。
- 同样，早期 Forge 审查中的问题已存在后续修复；迁移应参考修复后的源码和验证门，
  尤其是避免“能编译，但生产 JAR 没有加载 Mixin”。

## 为什么网络核心适合反向迁移

MiguelNetwork 传输的是 Minecraft TCP 字节流，wstunnel 位于独立进程。
它不解析或转换物品、区块、注册表、Forge 握手协议，因此大部分传输实现不依赖游戏版本。
这也意味着 **1.12.2 客户端仍需要对应的 1.12.2 服务端和兼容 Mod 集合**；共享 Discovery v1
不会让 1.12.2 客户端直接加入 1.21.1 服务器。

主工作树有 30 个生产 Java 文件，其中 9 个直接 import Minecraft/NeoForge。
其余 21 个没有直接 import，不代表完全独立：部分仍引用静态配置或 Mod 入口日志。

| 部分 | 现有实现 | 迁移工作量判断 |
|---|---|---|
| 进程与二进制 | `core/`，wstunnel 10.7.1、命令构造、解压和 SHA-256 | 低；保留资源路径、许可证、Windows/Linux x86-64 支持边界 |
| Discovery / 信任 | `discovery/`、`ClientDiscoveryService`、`ClientTrustStore` | 低到中；协议可复用，核对 Gson、路径和运行时库 |
| WS 探测 | `ClientTransportProbe` | 低；JDK 网络 API 可继续使用 |
| standalone 网关 | `StandaloneGateway`、`DiscoveryHttpServer` | 中；注入日志/配置，保留执行器关闭和双向转发行为 |
| 客户端编排 | `ClientTunnelManager` | 中；替换 FMLPaths、配置和兼容能力查询，保留路由策略 |
| 服务端编排 | `ServerTunnelController`、`DiscoveryDocumentProvider` | 中；适配生命周期、运行端口、目录和通告能力 |
| Loader 入口 / 配置 | `MiguelNetwork`、两个 Config 类 | 中；1.12.2 接口不同，不能机械替换包名前缀 |
| 连接注入 / 发行包 | `ConnectionMixin`、Gradle、metadata | 高；新目标、参数形态、加载阶段及生产 remap 均需验证 |
| ZstdNet | 两个 compatibility 类和专用 Mixin | 本阶段排除；没有本次核实的 1.12.2 构建与 API 证据 |

Java 21 可继续使用 records、`java.net.http.HttpClient`、`ProcessHandle`、`HexFormat`、Ed25519，
以及当前网关的 `Thread.ofVirtual()` / `Executors.newThreadPerTaskExecutor()`。
本路线不需要为了 Minecraft 1.12.2 把所有代码降为 Java 8。
仍须验证 Launcher 提供的 Java 运行环境包含 `java.net.http`、`jdk.httpserver` 等所需模块。

## 首要技术问题：1.12.2 连接注入

现有 `ConnectionMixin` 修改 `Connection.connect` 的单个 `InetSocketAddress` 参数。
Cleanroom **0.4.4-alpha 的实际 NetworkManager 补丁**显示客户端入口为：

```java
public static NetworkManager createNetworkManagerAndConnect(
        InetAddress address, int serverPort, boolean useNativeTransport)
```

由该签名得到的 MCP 描述符候选为：

```text
createNetworkManagerAndConnect(Ljava/net/InetAddress;IZ)Lnet/minecraft/network/NetworkManager;
```

签名有官方补丁证据；实际 SRG 映射、Netty 调用 owner/描述符、注入次数和生产可加载性尚未验证。
不要直接复制现代版本的 `method = "connect"`，也不要在没有目标字节码证据时写死 Netty 调用描述符。

实现建议与验收重点：

1. 对一次连接只计算一次路由，**同时替换 IP 和端口**。优先考察该方法内真正发起连接的调用点，
   用成对参数修改适配 `ClientTunnelManager`；避免两个独立参数注入各自启动一次 sidecar。
2. 原始服务器地址、实际解析后的目标地址和本地隧道地址应分别保存。
   Discovery audience、HTTP Host、WSS 主机名验证和信任缓存不能意外使用 `127.0.0.1`。
   特别测试域名、显式端口和 SRV；不能用反向 DNS 猜测用户原始主机名。
3. 保留 `C00Handshake` 中的原始主机和端口。官方补丁显示握手序列化附加 `\0FML\0`；
   重定向只能影响底层连接目的地，不应重写或丢弃该标记。
4. 分别验证服务器列表状态查询、正式登录、取消、断线重连及快速切换服务器。
   legacy ping 若有独立连接路径，应明确处理，不能用“能登录”替代列表状态验证。
5. 明确跳过单人世界本地连接；仅在 dedicated server 就绪时启动公网网关，保持现有范围。
6. 客户端专用 Mixin 与客户端类加载严格隔离，dedicated server 启动不能解析 GUI 或渲染类。
7. 在最小 Cleanroom 环境通过后，再测试目标整合包中的网络、登录界面和异步连接 Mod。
   同一方法上的多 Mod 注入冲突是主要兼容风险之一。

## Loader、配置与依赖

- 以 1.12.2 FML 的 `@Mod` / `@Mod.EventHandler` 生命周期适配入口；候选事件为
  `FMLPreInitializationEvent`、`FMLServerStartedEvent`、`FMLServerStoppingEvent`。
  具体服务器获取方式与客户端关闭钩子应从固定 Cleanroom 源码确认，不照搬 NeoForge 事件总线。
- 当前 `ModConfigSpec` 和 Forge 1.20.1 的 `ForgeConfigSpec` 都不能当作 1.12.2 原生配置 API。
  建议先提取配置值快照，再用旧 FML `Configuration` 或独立 TOML 适配器供值。
  如选择 `.cfg`，必须明确记录文件格式迁移；不能声称现有 TOML 自动兼容。
- 保留当前参数语义：`mode=STANDALONE`、`publicPort=35548`、`legacyFallback=true`，
  `discovery.signResponses=false`、`security.verifyDiscoverySignatures=false`、
  `security.enforceWssDowngradeProtection=false`。选择 WSS 时保留 TLS 证书验证行为。
- 主入口依赖 `com.mojang.logging.LogUtils`，进程管理依赖 SLF4J。
  改为平台提供日志接口/适配器，不能假设旧环境有相同 Mojang 日志库或 SLF4J binding。
- Gson、Netty、ASM、Mixin 等依赖以**固定 Cleanroom 发行环境**为准，不能套用原版 1.12.2 的旧依赖表。
  不向整合包全局打入另一份 Netty/Mixin/ASM；确需独立库时评估包隔离、许可证和类加载影响。
- 初版不通告 `zstdnet-primary`，也不装入现有 ZstdNet 反射适配/Mixin。
  未知 required filter 必须拒绝该路由；如果没有可用路由则停止连接，不能绕过能力检查。
  普通 Minecraft 自带的连接压缩/加密保持透明传输，不等于 ZstdNet 支持。

## 构建与代码组织建议

先使用固定 Java 21 历史模板建立隔离原型，再决定 common 提取范围。
原型通过前不必同时改造现有两条发布线。

```text
common/                       长期共享核心，Java 17
platform-neoforge-1.21.1/     现有 Java 21 平台
platform-forge-1.20.1/       已有 Java 17 适配经验
platform-cleanroom-1.12.2/   新增 Java 21 平台，独立 wrapper
```

这是建议布局，当前主工作树尚未建立这些模块。
如继续沿用 Java 17 common 方向，Java 21 的虚拟线程创建留在平台执行器工厂；
可以复用 Forge 修复中的并发准入、半关闭和停止测试，无需让 Cleanroom 平台放弃虚拟线程。
common → Loader 的反向引用应清除，配置、目录、日志和 Mod 能力由平台注入。

Java 21 历史模板与现有 MDG 构建属于不同工具链：

- 先独立执行各平台的 checked-in wrapper，不把主工程 wrapper 改成 9.3.1。
- Gradle composite 的一次调用仍使用同一个 Gradle 版本，不能用 `includeBuild` 冒充 wrapper 隔离。
- 历史模板 wrapper 未提供 `distributionSha256Sum`；实施时核对官方校验值并固定。
- 发布目标为 remap 后的 JAR；模板 `jar` 的 classifier 为 `dev`，不能直接分发开发包。
- 保留 Cleanroom 模板对应的 `ModType: CRL`、`MixinConfigs`、metadata 和 Mixin 注册路径。
  Java 21 模板使用 default/mod 两组 Mixin JSON；当前 Java 25 模板已改为单组 CleanMix 配置，
  不应混用两个时期的注册机制。
- 验证 Minecraft 范围 `[1.12.2]`、Loader 版本约束、Java class major 65、有效 refmap/映射、
  客户端专用注入和原生二进制资源。初期只声明经过验证的 Cleanroom 基线，不承诺普通 Forge 可加载。
- 发行名建议 `miguelnetwork-cleanroom-1.12.2-<mod_version>.jar`；版本仍来自根 `gradle.properties`。
  每个平台包带自己的 common 字节码、wstunnel 10.7.1 和许可证，用户无需另装 common JAR。

当前官方 `mixin` 模板核对结果为 Unimined 1.4.36-kappa、Gradle 9.7.0、Java 25，
其中 Loader 固定到 0.6.10-alpha，并非最新发布 0.6.13-alpha。
这些数字仅说明上游现状，不能替换前述 Java 21 历史组合。

## 实施顺序与通过条件

| 阶段 | 工作 | 通过条件 |
|---|---|---|
| A：平台基线 | 固定历史模板、Loader、Java 21、Fugue/Scalar 等需要的兼容组件版本 | 空 Mod 在开发 client/server 和独立安装的生产环境加载成功 |
| B：服务端传输 | 接入配置、sidecar、Discovery 和 standalone 网关 | 默认无签名 WS 单端口成功，日志和端口冲突处理正确 |
| C：客户端接入 | NetworkManager 专用 Mixin、路由和地址上下文 | 生产 JAR 能完成状态查询、登录、取消和重连，握手地址/FML 标记不变 |
| D：整合包与发布门 | 网络故障、并发、Windows/Linux、目标 Mod 集合 | 无进程/连接泄漏，required filter 和无效 Discovery 不绕过，包检查及哈希通过 |

默认路径验收必须出现：

```text
standalone WS gateway is ready
Discovery and wstunnel share one port
selected Discovery route
```

`Discovery unavailable`、`selected legacy ... route`、`discovered vanilla TCP endpoint`
表示进入回退路径，不能计作默认 Discovery 验收成功。
另测 `legacyFallback` 开/关、未知 required filter、无效 manifest、可选签名及信任存储、
可选外部代理 WSS 的有效证书和证书失败。WSS/TLS 始终是可选部署，Mod 不管理证书。

Windows 和 Linux x86-64 都需验证 sidecar 启停、二进制哈希、双向大流量、并发连接、半关闭、
服务端退出、重连和端口占用；本机 Windows 结果不能替代 Linux 结果。
Cleanroom 客户端与普通 Forge 1.12.2 服务端的组合，可作为后续独立测试项；
字节隧道原则上不要求两端同 Loader，但 FML/Mod 握手兼容性必须另证，不能提前承诺。

工作量判断：这是**中等规模的平台移植**，不是修改版本号即可完成。
单人熟悉代码且依赖可正常下载时，可按约 1–2 周工程时间规划基础移植与验证；
复杂整合包冲突、Linux 实机覆盖和冻结 Loader 的长期维护不包含在此估计内。
最值得先投入的是阶段 A 与 C 的最小连接实验，它们能最快确认工具链和注入风险。

## 本轮验证与限制

本轮完成 Git/源码/本地验证记录阅读、官方版本与模板核对、现有 JAR SHA-256 复算。
没有执行 Gradle 构建、启动 Cleanroom、安装 Java/Loader/Mod、修改生产源码、提交或发布。
官方 Wiki 迁移指南链接本轮返回 404；结论以能直接读取的官方仓库、模板和发布说明为依据。

复算的**既有产物**，仅用于识别现有基线：

| 所属工作树 | 产物相对路径 | SHA-256 |
|---|---|---|
| NeoForge 主工作树 | `build/libs/miguelnetwork-neoforge-1.21.1-0.2.0.jar` | `A4FE4133E04E5815040B68F500E25C5BBBC5A9BA928DA3763727B9E501837796` |
| Forge 修复工作树 | `build/libs/miguelnetwork-forge-1.20.1-0.2.0.jar` | `8AE534F5195AD592247B722EA3438DFA29450408DDBBE085838BF55FAEF90E36` |

本轮没有 Cleanroom 发行 JAR，因此也没有该平台的发行哈希或运行支持声明。

## 官方证据

1. [Cleanroom 0.4.4-alpha 源码及 README](https://github.com/CleanroomMC/Cleanroom/tree/0.4.4-alpha)：Java 21、客户端/服务端安装范围。
2. [0.5.0-alpha 发布说明](https://github.com/CleanroomMC/Cleanroom/releases/tag/0.5.0-alpha)：迁移 Java 25。
3. [0.6.13-alpha 发布](https://github.com/CleanroomMC/Cleanroom/releases/tag/0.6.13-alpha)与[当前 README 快照](https://github.com/CleanroomMC/Cleanroom/blob/19b9fa8f5042357efc9216f68204b368f7a03d90/README.md)：当前最新版本和 Java 25+。
4. [Java 21 Mixin 模板固定提交](https://github.com/CleanroomMC/CleanroomModTemplate/tree/df9ead105ca1aaf4fef10bcf6d2e50d2d160e5b0)、[build.gradle](https://github.com/CleanroomMC/CleanroomModTemplate/blob/df9ead105ca1aaf4fef10bcf6d2e50d2d160e5b0/build.gradle)、[wrapper](https://github.com/CleanroomMC/CleanroomModTemplate/blob/df9ead105ca1aaf4fef10bcf6d2e50d2d160e5b0/gradle/wrapper/gradle-wrapper.properties)。
5. [当前 Mixin 模板](https://github.com/CleanroomMC/CleanroomModTemplate/tree/mixin)：本次读取的 Java 25 / CleanMix / Unimined 现状，分支内容会变化。
6. [NetworkManager 补丁](https://github.com/CleanroomMC/Cleanroom/blob/0.4.4-alpha/patches/minecraft/net/minecraft/network/NetworkManager.java.patch)：连接入口签名。
7. [C00Handshake 补丁](https://github.com/CleanroomMC/Cleanroom/blob/0.4.4-alpha/patches/minecraft/net/minecraft/network/handshake/client/C00Handshake.java.patch)：握手主机及 FML 标记。

相关项目设计：[跨版本计划](CROSS_VERSION_PLAN.md)、[Discovery 协议](DISCOVERY_PROTOCOL.md)、
[ZstdNet 兼容边界](ZSTDNET_COMPATIBILITY.md)。跨版本计划中的进度和版本快照需结合本次工作区核对结果解读。
