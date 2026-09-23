# MiguelNetwork — Cleanroom 1.12.2

本分支正在将 MiguelNetwork 0.2.0 移植到 **Minecraft 1.12.2 / Cleanroom 0.6.13-alpha / Java 25**。
迁移状态和验证限制见 [Cleanroom 迁移记录](docs/CLEANROOM_MIGRATION.md)。旧版 NeoForge 的运行记录不代表本平台已经通过游戏实测。

MiguelNetwork 用内置的 wstunnel 10.7.1，将 Minecraft TCP 字节流经 WebSocket 传输。
默认和推荐部署是 **STANDALONE**：内置网关在一个公网端口同时提供 Discovery v1 和明文 WS。
无需证书或外部代理。需要 WSS 的运营者可选择 `EXTERNAL_PROXY`，由 Nginx/Caddy 等外部网关终止 TLS。
Mod 不管理证书，签名 Discovery 和防降级策略默认关闭。

可选的 ZstdNet Cleanroom `1.4.7-cleanroom-alpha.1` 适配器会在 Discovery 广告
`zstdnet-stream` 时先建立 MiguelNetwork 隧道，再交给 ZstdNet 负责压缩；未识别的必需 filter 仍会拒绝。
没有 ZstdNet 时，客户端选择普通 Minecraft 路由。Minecraft 本身的压缩与加密字节流保持透明转发。
客户端和服务端需要相匹配的 Minecraft 1.12.2 / Mod 集合；这不是不同 Minecraft 版本之间的协议转换器。

## 运行目标

- Java 25、Cleanroom 0.6.13-alpha、Minecraft 1.12.2。
- Windows x86-64 和 Linux x86-64；这也是内置 wstunnel 二进制的范围。
- 将同一发行 JAR 放入客户端和 dedicated server 的 `mods` 目录。
- 普通 Forge / Java 8 不在支持范围内。按 Cleanroom 官方说明准备该 Loader 所需的兼容组件。
- 不向单人世界自动开放公网网关。

## 使用

服务端保留正常的 `server.properties`，例如：

```properties
server-ip=127.0.0.1
server-port=25565
```

启动后会创建 `config/miguelnetwork-server.toml`；默认公网端口为 **35548**，必须不同于游戏后端端口。
开放/转发公网 TCP 35548，客户端服务器列表填写 `域名或IP:35548`。
客户端首次使用会创建 `config/miguelnetwork-client.toml`，正常连接通过 Discovery 自动选择 WS 路由。

服务端日志应包含 `standalone WS gateway is ready` 和 `Discovery and wstunnel share one port`；
客户端应包含 `selected Discovery route`。`selected legacy` 表示使用了回退路径。
`legacyFallback=false` 时，Discovery 不可用会停止连接。

配置在启动时读取；现有 TOML 不会被静默重写，格式或值无效时报告错误。
完整配置和诊断见 [配置说明](docs/CONFIGURATION.md) 与 [用户指南](docs/USER_GUIDE.md)。

## 构建

使用 Java 25 和仓库中的 Gradle 9.7.0 wrapper：

```powershell
.\gradlew.bat test
.\gradlew.bat build --no-daemon
```

Linux 使用 `./gradlew`。版本来源为 `gradle.properties` 的 `mod_version`。
MCP 的历史合并工具另需 JDK 8 构建辅助环境，可由 Foojay 自动下载或按 [开发说明](docs/DEVELOPMENT.md) 指定；游戏与 Mod 仍使用 Java 25。
发行文件为 `build/libs/miguelnetwork-cleanroom-1.12.2-<mod_version>.jar`。
`-dev.jar` 和 `-shadow-dev.jar` 是开发中间产物，不应安装或发布。
Unimined 1.4.36-kappa 负责 MCP stable 39-1.12 和生产 remap；NightConfig 随包重定位以隔离配置库。

## English

This branch ports MiguelNetwork to Minecraft 1.12.2, Cleanroom 0.6.13-alpha and Java 25.
The default standalone gateway serves Discovery v1 and plain WebSocket on one public port (35548).
WSS is optional and terminated by an external proxy. The optional ZstdNet Cleanroom
`1.4.7-cleanroom-alpha.1` adapter composes the advertised `zstdnet-stream` route; unknown required filters remain rejected.
Use the remapped production JAR on matching client/server installations. See the migration record for completed checks
and outstanding game/Linux validation; historical NeoForge validation does not establish Cleanroom compatibility.

## License

MiguelNetwork uses Apache-2.0. Bundled wstunnel 10.7.1 uses BSD-3-Clause; its binary hashes and required notices
remain checked by the distribution task. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
