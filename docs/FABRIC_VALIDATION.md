# Fabric 迁移验证记录

日期：2026-09-17。Mod 版本：0.2.0。状态：迁移实现及 Windows 自动验证完成，仍为技术预览。

## 实现范围

- Fabric Loader 0.16.10、官方 Mojang mappings、Loom 1.7.4、带 SHA-256 的 Gradle 8.9 wrapper。
- Fabric 生命周期与客户端入口隔离；网关在默认 SERVER_STARTED 阶段之后启动。
- 内嵌 NightConfig core/toml 3.6.4，保留配置键、默认值和系统属性覆盖；已有 TOML 不重写。
- ZstdNet 1.4.7/1.4.8 发布物 API 检查及可选 Mixin 门控；代理串联失败时拒绝已选定路由。
- 原生 wstunnel 10.7.1、默认 STANDALONE/未签名 Discovery/WS、可选 WSS 均保留。
- RAW 路由使用 ZstdNet RAW 代理；压缩路由使用 ZSTD，代理句柄交回 ZstdNet 管理。
- 分发包包含 native、许可证、NightConfig 对应源码及嵌套库；没有 Forge/NeoForge 入口和 metadata。

## 自动验证

`gradlew.bat build exportSmokeClasspath --no-daemon` 成功。
本地 Java 工具链及预下载 wstunnel 目录通过命令行属性提供，不写入仓库。
每条支持线 42 项 JUnit 测试：41 通过、0 失败、0 错误、1 跳过。
跳过的是需要 `MIGUELNETWORK_TEST_ENDPOINT` 的可选真实端点测试。

分发检查覆盖生产 remapJar、精确 MC/Java metadata、intermediary ConnectionMixin refmap、
平台类引用、类字节码版本、两个 native 哈希、NightConfig 嵌套库、许可证和源码归档。

使用仓库 `scripts/ProductionSmoke.java` 与 `scripts/production-smoke.ps1`，
以 `fabric.development=false` 启动真实 Knot/Fabric/Mixin 加载器，加载生产 Mod JAR、
原始 Minecraft JAR、Fabric API 及相应 ZstdNet 发布 JAR。
没有把命名空间仍为 named 的开发包当作生产包测试。

| 场景 | 结果 |
|---|---|
| CLIENT，无 ZstdNet | 入口初始化、Connection Mixin 转换、Discovery/raw WS 双向传输通过 |
| CLIENT，ZstdNet 1.4.7 | 可选 Mixin 实际应用，RAW 与 ZSTD 两条真实字节流路径通过 |
| CLIENT，ZstdNet 1.4.8 | 可选 Mixin 实际应用，RAW 与 ZSTD 两条真实字节流路径通过 |
| SERVER，无 ZstdNet | 纯服务端生产加载和本 Mod 初始化通过，无客户端类加载故障 |

字节流测试使用真实 wstunnel 进程、内置 StandaloneGateway、Discovery 服务、
ZstdNet 客户端代理和（压缩场景）其服务端代理。每次双向校验 32 KiB 确定性随机数据；
检查正常直连钩子、模拟服务器列表的 bypass + ServerData 地址恢复、代理句柄交接、
RAW/ZSTD 模式以及内部 loopback 不被再次重定向。测试不创建游戏世界。
压缩模式允许 ZstdNet 自身附加真实 IP 扩展，但验证扩展前的逻辑握手主机、端口和协议版本保持不变。

最终成功标记为 `MIGUELNETWORK_TRANSFER_OK` 与 `MIGUELNETWORK_PRODUCTION_SMOKE_OK`。
每次验证创建独立的 `run/production-smoke-*` 目录，日志和配置均不入 Git。

## 复现

先构建及导出依赖，再使用目标 JDK 执行：

```powershell
.\gradlew.bat build exportSmokeClasspath --no-daemon
.\scripts\production-smoke.ps1 -Side CLIENT -JavaHome <目标JDK目录>
.\scripts\production-smoke.ps1 -Side CLIENT -JavaHome <目标JDK目录> -ZstdJar <对应Fabric发布JAR>
.\scripts\production-smoke.ps1 -Side SERVER -JavaHome <目标JDK目录>
```

默认 Gradle 缓存为用户的 `.gradle`，自定义缓存时传入 `-GradleHome`。
ZstdNet 文件 ID 和 SHA-256 见 [兼容说明](ZSTDNET_COMPATIBILITY.md)。

## 已知限制与发布前剩余项目

1. 未启动图形客户端或完整 dedicated-server 世界；真实进服、服务器列表点击、status ping、
   取消连接、连续重连及整合包中的生命周期仍需游戏内验收。
   模拟 bypass/ServerData 只证明适配钩子行为，不能代替整个 UI/代理回收生命周期。
2. 未执行 Linux 游戏/进程运行验证，也未在本轮运行真实 EXTERNAL_PROXY/WSS、证书失败和签名部署。
   单元测试及保留实现不等于这些部署已经重新验收。
3. 在早期隔离测试中，ZstdNet 1.4.8 的服务端代理在连接结束后立即调用 stop，
   可触发其 ServerProxyRuntime 内 guard/stats 已清空的 NullPointerException。
   最终测试先等待连接工作线程退出后停止，因此不据此宣称上游的强制停服竞态已修复。
   未修改 ZstdNet JAR；真实服务器停服场景仍属于发布验收门。
   1.20.1 的最终压缩测试在数据校验完成后的关闭阶段还记录了 ZstdNet 的
   `ZstdIOException: Truncated source` 警告；数据往返断言通过，但不能把该结果解释为关闭路径无警告。
4. 尚未在完整 Fabric 服务器中验证两个 Mod 的启动/停止事件全流程；
   测试直接启动实际代理和网关，并检查相同的生产类型及方法。
5. 没有修改或重建现有 main/NeoForge 与 1.20.1/Forge 产物。
   没有提交、tag、push 或发布。当前分支的 release 工作流仅手动验证和上传 CI 产物。

## 构建环境和产物

- 分支：`codex/fabric-1.21.1`；Windows x86-64；目标 JVM：Microsoft OpenJDK 21.0.12.1。
- Minecraft 1.21.1，Fabric API 0.116.10+1.21.1。
- 产物：`build/libs/miguelnetwork-fabric-1.21.1-0.2.0.jar`。
- SHA-256：`B1195F18D8A4C015F54BDA2414DAA51F9A01CEB6EE919FB393FE5623785A4539`。
