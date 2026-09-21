# 跨 Minecraft 版本迁移研究与实施门

更新：2026-09-12。状态：研究与设计，尚未授权实施。本文不表示新版本已支持或已通过运行验证。

## 结论与范围

- 核心支持线：Forge 1.20.1 / Java 17，以及现有 NeoForge 1.21.1 / Java 21。
- 现代滚动线：NeoForge 1.21.11 / Java 21；1.21.4、1.21.8 按真实整合包需求增加。
- 后续观察：NeoForge 26.1.2 / **Java 25**。旧研究表中的 Java 21 已被官方 MDK 核对结果纠正。
- 使用普通 Gradle 多项目，不引入 Architectury；共享核心编译到 Java 17。
- 每个 Minecraft 版本、Loader 发布独立 JAR，Minecraft metadata 使用精确版本。
- 本轮只更新文档；不拆模块、不升级依赖、不修改 Mixin、metadata 或现有发布版本。

迁移保留产品契约：`STANDALONE` 为推荐部署，在 `server.publicPort` 上复用 Discovery 与明文 WS。
WS 是正式支持的传输。`EXTERNAL_PROXY` 可由外部网关终止 WSS，私有 hop 仍为 WS；Mod 不管理证书。
签名 Discovery、TLS 和防降级是可选控制，不因缺少加密或签名而拒绝默认连接。
Discovery v1 是正常控制路径；只有 Discovery 不可用且 `legacyFallback` 启用时才允许旧探测回退。
无效 manifest、必需但不支持的 filter 和不支持的兼容 API 不得导致连接未通告的目标。

## 目标版本与证据日期

社区数量沿用用户提供的 **2026-09-07 Modrinth 同口径项目数快照**；本轮没有重新查询数量。
原始查询参数和响应未随交接提供，因此不能把这些数值视为可复现的最新统计，也不能等同于活跃玩家数。
后续重采样应保存 `project_type=mod`、Minecraft 版本、Loader facets、查询时间和 `total_hits`，
确认与旧快照口径一致后再比较；同一项目可能计入多个版本，不可相加为独立项目总量。

| Minecraft | Loader | 项目数（旧快照） | 支持优先级 |
|---|---|---:|---|
| 1.20.1 | Forge | 27,551 | 核心 |
| 1.21.1 | NeoForge | 22,278 | 核心，已有实现 |
| 1.21.4 | NeoForge | 9,232 | 按整合包需求 |
| 1.21.8 | NeoForge | 8,888 | 按整合包需求 |
| 1.21.11 | NeoForge | 7,731 | 现代滚动线 |
| 1.21.5 | NeoForge | 7,490 | 暂不单列支持线 |
| 26.1.2 | NeoForge | 7,212 | 后续观察 |
| 1.21.10 | NeoForge | 7,024 | 暂不单列支持线 |

2026-09-12 只读核对的官方版本信息如下。远端 `main` 和 latest 会变化；实际开发时须固定版本及来源 commit。

| Minecraft | Loader 基线或模板版本 | Java | 证据与说明 |
|---|---|---:|---|
| 1.20.1 | Forge recommended 47.4.10；latest 47.4.23 | 17 | Forge promotions 已核对；推荐以 47.4.10 编译并覆盖 47.4.23 |
| 1.21.1 | NeoForge MDK 与 Maven 均为 21.1.250 | 21 | 旧快照 MDK 为 21.1.249；现有工程仍为 21.1.77 |
| 1.21.4 | NeoForge 21.4.157 | 21 | 官方 MDK |
| 1.21.8 | NeoForge 21.8.54 | 21 | 官方 MDK |
| 1.21.11 | NeoForge 21.11.45 | 21 | 官方 MDK |
| 26.1.2 | NeoForge 26.1.2.107 | 25 | 官方 MDK；替代旧快照 26.1.2.104 / Java 21 |

上述现代 MDK 均使用 ModDevGradle 2.0.146、Gradle 9.2.1。
Forge 1.20.1 模板的 MDG Legacy 2.0.91 / Gradle 8.14.5 / Java 17 组合，以及模板中的 Forge 47.1.3，
来自交接研究，本轮未独立复核模板：候选 raw 地址返回 404，GitHub 仓库查找遇到 403/429 限流。
将其作为隔离构建的候选回退组合；实施前必须找到准确模板仓库并固定 commit，不能称为本工程已验证组合。
Legacy 插件的支持范围、SRG reobfuscation 和 Mixin 要求已直接核对官方文档。

## 当前工程与共享边界

本轮本地检查：`mod_version=0.2.0`，Minecraft 1.21.1，NeoForge 21.1.77，MDG 1.0.21，
Gradle wrapper 8.9，Java 21；Minecraft 范围仍为 `[1.21.1,1.22)`，NeoForge 范围为 `[21.1.0,)`。
这些是现状，不是迁移后建议的支持承诺。

`src/main/java` 当前有 **30 个** Java 文件，9 个直接 import Minecraft/NeoForge，21 个没有直接 import。
旧快照的 26 / 9 / 17 已过时。计数只表示语法依赖，不能证明 21 个文件可直接迁移。

| 边界 | 当前类/目录 | 迁移工作 |
|---|---|---|
| Loader/Minecraft 直接依赖（9 个） | `MiguelNetwork`、`ClientTunnelManager`、两个 ZstdNet compatibility 类、两个 Config 类、两个 Mixin、`ServerTunnelController` | 生命周期、配置注册、路径与 Mod 查询留在平台；提取其中的通用路由与进程编排 |
| 优先迁移的 18 个候选 | `core/` 7 个、`discovery/` 6 个、`ClientDiscoveryService`、`ClientTransportProbe`、`ClientTrustStore`、`DeploymentMode`、`ServerEndpointResolver` | 验证 Java 17 API、显式库依赖和包可见性；沿用已传入的路径/日志依赖 |
| 间接配置耦合 | `DiscoveryDocumentProvider`、`DiscoveryHttpServer` | 用不依赖 Loader 的配置值快照替代静态 `ServerConfig`；通告路由由平台提供能力信息 |
| 间接入口与 JDK 耦合 | `DiscoveryHttpServer`、`StandaloneGateway` | 注入日志；网关的 Java 21 线程创建下沉到平台执行器工厂 |

建议布局（尚未创建）：

```text
MiguelNetwork/
  common/                    Java 17
  platform-forge-1.20.1/     Java 17
  platform-neoforge-1.21.1/  Java 21
  platform-neoforge-1.21.11/ Java 21
  integration-tests/
  packaging/
```

`common` 包含二进制选择/解压/哈希、命令构造、sidecar 生命周期、Discovery 编解码/签名/信任存储、
WS/WSS 探测与路由策略、配置值模型和网关协议逻辑，以及大多数单元测试。
平台负责入口、Loader 事件、配置注册、游戏目录、Mod 列表与版本、Minecraft 类型、Mixin 和 metadata。
构建时显式声明 Gson、SLF4J API 等依赖，检查 Java 17 兼容性；不再依靠 NeoForge 编译类路径隐式供给。
如继续使用 `com.sun.net.httpserver`，Java 17 运行测试须覆盖 `jdk.httpserver` 可用性。

依赖方向只能为平台 → common。common 接收 `Path`、配置值、日志和通用能力描述，不能回调静态入口类、
`FMLPaths` 或 Loader 配置。`MiguelNetworkProtocol.ZSTDNET_FILTER` 可作为协议标识保留；
具体 ZstdNet 类名、反射调用、版本门控及连接组合留在 1.21.1 平台。

### Java 17 网关前置问题

`StandaloneGateway` 使用 `Executors.newThreadPerTaskExecutor`、`Thread.ofVirtual`、`Thread.ofPlatform`，
不能只改 toolchain 就放入 Java 17 common。建议由平台传入基于 Java 17 接口的执行器/线程工厂：
1.21.1 平台维持虚拟线程行为，Forge 平台提供 Java 17 实现，并明确关闭时的执行器所有权。

网关在一个连接任务内向同一执行器提交反向复制任务，再等待其结束。
不能直接替换为普通固定线程池：长连接占满线程后，反向任务可能排队，造成线程饥饿。
Forge 方案须确保双向复制任务都有执行容量，并验证并发准入、拒绝后的 socket 清理、半关闭及服务器退出。
选型以单连接、双客户端和并发压力验证为门，不在本轮实现。Java 17 可使用 `ProcessHandle`，无需 Java 8 诊断降级。
最终必须以 `--release 17` 编译并在真实 Java 17 上测试；仅检查 import 不足以证明兼容。

## Forge 1.20.1 接入与生产 JAR

| 项目 | Forge 路线 |
|---|---|
| Gradle 插件 | `net.neoforged.moddev.legacyforge`；`legacyForge.version` 使用 `1.20.1-47.4.10` |
| 配置/事件 | `ForgeConfigSpec`、`MinecraftForge.EVENT_BUS`；按选定 Forge 基线核对 `FMLJavaModLoadingContext` / `ModLoadingContext` |
| metadata | `META-INF/mods.toml`；Minecraft 精确范围 `[1.20.1]` |
| Mixin | `JAVA_17`；annotation processor、refmap 生成及 JSON 引用；manifest 声明 `MixinConfigs` |
| 生产映射 | SRG reobfuscation；发布 `reobfJar` 输出，不使用 `build/devlibs` 开发包 |
| 验收 | 开发 client/server 与安装到真实 Forge 的生产 JAR 都必须通过 |

Legacy 官方文档确认支持 Forge 1.17–1.20.1，并自动为 `jar` 配置 reobfuscation；
生产包仍需检查 refmap 内容、MixinConfigs、目标类和资源，不能把任务成功等同于生产可加载。

1.20.1 的映射表显示：Mojang 命名下二参数方法为
`connectToServer(InetSocketAddress, boolean): Connection`，三参数方法为
`connect(InetSocketAddress, boolean, Connection): ChannelFuture`。
Yarn 将两者都命名为 `connect`；“同名二/三参数重载”须说明映射命名空间。
当前项目 Mixin 仅写 `method = "connect"`，迁移建议明确三参数 Mojang 描述符：

```text
connect(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;
```

该描述符来自映射签名核对，尚未通过 Forge 转换后的源码、字节码或运行实验确认。
实施时检查实际 Netty 创建路径，验证 status ping、登录、取消和重连；断言每次连接只重定向一次，
并保留原始 Minecraft 握手主机和端口。1.21.11、26.1.2 必须分别重查，不能复制此描述符作为支持依据。

## 构建验证门与实施顺序

建议首先尝试多项目统一 MDG 2.0.146 / Gradle 9.2.1，Gradle 运行 JVM 与各模块 toolchain 分开管理。
建议使用 Java 21 启动当前多项目的 Gradle，Forge/common 由 Java 17 toolchain 编译运行。
未来 26.1.2 另设 Java 25 toolchain 和运行环境。
统一构建仍是假设，正式加入 Forge 前须通过以下技术验证门：

1. Forge 1.20.1 `runClient` 与 `runServer` 启动。
2. `reobfJar` 成功，refmap 有实际映射并进入生产 JAR，manifest 正确声明 MixinConfigs。
3. 47.4.10、47.4.23 的生产 Forge 客户端和 dedicated server 能加载、Ping、登录并传输数据。
4. common 和 Forge 字节码兼容 Java 17；JAR 无另一 Loader 的入口、Mixin 或 metadata。

若统一 Legacy 路径失败，记录首个失败及版本/日志证据，在独立 composite included build 中使用经核实的
Forge 模板组合，不降低所有现代模块。**同一次 composite 调用共享启动它的 Gradle 版本，不会执行子构建 wrapper**；
如果问题在 Gradle 9.2.1，Forge 必须通过自己的 8.14.5 wrapper 独立调用。
此时由 Java 17 common 产物或独立构建的 common 项目提供共享代码，CI 分开构建和验证；
不能把“放进 composite”当作 Gradle 版本隔离已经实现。

授权实施后按阶段推进，每阶段通过才进入下一阶段：

1. 更新设计与版本决策（本轮文档工作）。
2. 保持 NeoForge 1.21.1 / 21.1.77、MDG 1.0.21、现有行为，先提取 Java 17 common。
3. 验证现有单元测试、standalone WS/Discovery、可选代理 WSS 和受支持 ZstdNet 路径不回退。
4. 单独升级构建工具和 1.21.1 编译依赖；旧建议候选 21.1.249 保留在矩阵，当前 MDK 为 21.1.250。
   升级编译依赖不自动证明旧 Loader 可运行：若无法保持旧基线 API，优先保留旧编译基线，
   或另行记录提高运行下限的决定，并同步 metadata。
5. 回归无 ZstdNet 的 NeoForge 21.1.77、ATM10 所用 21.1.215，以及 21.1.249 / 250。
6. 完成上述 Legacy 构建门，再建立 Forge 1.20.1 模块并验证 47.4.10 / 47.4.23。
7. 增加 NeoForge 1.21.11，重新验证生命周期、连接路径、Mixin 和生产 JAR。
8. 根据实际整合包需求决定 1.21.4 / 1.21.8，最后评估 Java 25 的 26.1.2。

## ZstdNet 与回归矩阵

现有适配仅覆盖 Minecraft 1.21.1 / NeoForge / ZstdNet 1.4.7、1.4.8 的精确 API 结构。
其 JAR metadata 要求 NeoForge **21.1.221 或更新**，所以 21.1.77 和 ATM10 的 21.1.215
回归行必须不装这两个 ZstdNet 构建；整合包历史运行记录不能覆盖依赖下限。

| 验证行 | ZstdNet | 必须覆盖 |
|---|---|---|
| NeoForge 1.21.1 / 21.1.77、215、249、250 | 无 | 原始 Minecraft route、默认 WS/Discovery、可选代理 WSS |
| NeoForge 1.21.1 / 21.1.221、249、250 | 1.4.7、1.4.8 分别验证 | 原始状态路由、压缩登录路由、单次重定向、关闭/重连 |
| Forge 1.20.1 / 47.4.10、47.4.23 | 不声明兼容 | 开发及生产 JAR，Java 17，WS/Discovery 与可选代理 WSS |
| NeoForge 1.21.11 / 21.11.45 | 不声明兼容 | 新连接路径、开发及生产 JAR，Java 21 |

未知/不支持的 ZstdNet 或 API 不得被标为支持，也不得让 required filter 绕过能力检查。
其他 Minecraft 版本只有在找到对应构建、核对 API 和连接路径并完成运行测试后，才新增平台专用适配。

Windows/Linux x86-64 均覆盖二进制哈希、进程启动/退出、端口冲突、并发双向传输与网络中断。
默认未签名 WS Discovery 必须成功；另测可选签名与信任存储、未知 required filter、无效 manifest、
Discovery 不可用时 `legacyFallback` 开/关，以及 WSS 的有效证书和证书失败。
诊断沿用 `standalone WS gateway is ready`、`Discovery and wstunnel share one port`、
`selected Discovery route`；出现 `selected legacy` 或 `discovered vanilla TCP endpoint` 不算 Discovery 路径验收通过。

## 发布边界与未完成验证

每个平台 JAR 内包含 common 字节码、该平台资源及固定 wstunnel 10.7.1 Windows/Linux x86-64 二进制和许可证。
用户不需要另外安装 common JAR。打包验证必须覆盖重复资源、错误平台入口、native 哈希、许可证、metadata、
Minecraft 精确范围和字节码版本；`mod_version` 继续由根 `gradle.properties` 提供。

示例产物名：`miguelnetwork-forge-1.20.1-<mod_version>.jar`、
`miguelnetwork-neoforge-1.21.1-<mod_version>.jar`、`miguelnetwork-neoforge-1.21.11-<mod_version>.jar`。
Minecraft 范围分别为 `[1.20.1]`、`[1.21.1]`、`[1.21.11]`；Loader 下限单独声明并由矩阵证明。
当前过宽的 `[1.21.1,1.22)` 和低于编译基线的 `[21.1.0,)` 留待授权实施时修订。
不承诺不同 Minecraft 版本客户端之间互联；传输核心共享不转换游戏协议或整合包内容。

本轮只完成静态审查、资料核对与设计更新，没有执行 Gradle、新版本 runClient/runServer、reobfJar、
生产 Forge 实例或 Java 17 编译。后续验收需记录每个生产 JAR 的准确路径及 SHA-256，不能引用本轮文档作为运行证据。
提交、tag、push、发布仍须用户明确授权对应操作。

## 参考资料

- [ModDevGradle Legacy 官方文档](https://github.com/neoforged/ModDevGradle/blob/main/LEGACY.md)：支持范围、SRG、AP/refmap、MixinConfigs。
- [Forge promotions](https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json)：recommended/latest。
- [Forge 1.20.x 开发起步](https://docs.minecraftforge.net/en/1.20.x/gettingstarted/)：Forge/Java 开发背景。
- [NeoForge 1.21.1 MDK](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle)。
- [NeoForge 1.21.4 MDK](https://github.com/NeoForgeMDKs/MDK-1.21.4-ModDevGradle)。
- [NeoForge 1.21.8 MDK](https://github.com/NeoForgeMDKs/MDK-1.21.8-ModDevGradle)。
- [NeoForge 1.21.11 MDK](https://github.com/NeoForgeMDKs/MDK-1.21.11-ModDevGradle)。
- [NeoForge 26.1.2 MDK 的 Java toolchain](https://github.com/NeoForgeMDKs/MDK-26.1.2-ModDevGradle/blob/main/build.gradle)。
- [NeoForge Maven 元数据](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)。
- [Minecraft 1.20.1 Connection 映射表](https://mappings.dev/1.20.1/net/minecraft/network/Connection.html)：第三方映射索引，非运行验证。
- [当前开发说明](DEVELOPMENT.md)、[ZstdNet 兼容边界](ZSTDNET_COMPATIBILITY.md)、[当前验证记录](VALIDATION_REPORT.md)。
