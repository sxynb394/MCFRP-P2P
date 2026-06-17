# MC FRP P2P

Minecraft 1.20.1 Fabric 模组，通过 frp XTCP 协议实现免公网 IP 局域网联机。

> 当前公开版本：**v0.1.1** · 配套 Minecraft 1.20.1 · 需要 fabric-api

## 功能特性

- **一键开服**：单人世界 → 对局域网开放 → 自动写入 P2P 隧道 → 聊天栏吐出可点击的邀请码
- **一键加入**：多人游戏 → 通过邀请码加入 → 粘贴邀请码 → 自动建立反向 XTCP 连接
- **零配置内嵌**：frpc 进程由模组在游戏内启停，关闭世界/退出游戏时自动清理
- **加密安全**：邀请码包含 `secretKey`，未授权访客无法连入
- **不破坏原版**：仅在检测到 `MCFRP://` 邀请码时介入，原始联机逻辑完全保留

## 前置要求

- Minecraft **1.20.1**
- Java Development Kit (JDK) **17**（构建和运行游戏都需要）
- Fabric Loader ≥ 0.15
- fabric-api ≥ 0.92
- 一台具有公网 IP 的服务器，用于运行 frps

## 使用步骤

### 1. 部署 frps 服务端

在公网服务器上下载 [frp v0.52+](https://github.com/fatedier/frp/releases)，配置最小化 `frps.ini`：

```ini
[common]
bind_port = 7000
token = mcfrp_default
```

启动：`frps -c frps.ini`

### 2. 编辑客户端配置

在 `.minecraft/config/mcfrp.properties` 中写入：

```properties
frps.host=your.vps.com
frps.port=7000
frps.token=mcfrp_default
```

将 `your.vps.com` 替换为你的 frps 公网域名或 IP。

### 3. 准备 frpc 二进制

本仓库不包含 frpc 可执行文件（避免大型二进制与许可证冲突），请从 [frp Releases](https://github.com/fatedier/frp/releases) 下载对应平台的 frpc，按下表放入 `src/main/resources/assets/mcfrp/native/`：

| 平台 | 源文件名 | 目标文件名 |
|------|----------|-----------|
| Windows | `frpc.exe` | `frpc-windows.exe` |
| macOS (Intel) | `frpc` | `frpc-macos-intel` |
| macOS (Apple Silicon) | `frpc` | `frpc-macos-arm` |
| Linux | `frpc` | `frpc-linux` |

### 4. 构建

```bash
./gradlew build
```

Windows 用户：

```bat
gradlew.bat build
```

产出位于 `build/libs/mc-frp-p2p-0.1.1.jar`。

### 5. 安装与使用

1. 把构建得到的 jar 文件放入游戏 `mods` 文件夹
2. 启动 Minecraft 并加载该模组
3. **房主**：进入单人世界 → 按 `Esc` → 选择「对局域网开放」→ 聊天栏出现 `MCFRP://...` 形式的邀请码 → 点击即可复制到剪贴板
4. **访客**：将房主发来的邀请码粘贴到聊天栏 / 「通过邀请码加入」入口 → 模组自动接管并直连房主
5. 关闭世界或退出游戏时，frpc 子进程会被自动清理，无残留

## 工作原理

```
┌──────────┐                              ┌─────────┐              ┌──────────┐
│  房主端  │  frpc (XTCP visitor)         │  frps   │              │ 访客端   │
│ Minecraft│◄────────────────────────────┤  公网   │◄──────────── │ Minecraft│
│ :local   │  通过同一 secretKey 打洞      │  7000   │  XTCP 客户端 │ (无公网) │
└──────────┘                              └─────────┘              └──────────┘

邀请码 = base64(binary(frpsAddr, frpsPort, secretKey, serverName)) + "MCFRP://" 前缀
```

- 房主开放 LAN 时，模组在后台启动 frpc，建立一条 XTCP 隧道并开放本地游戏端口
- 邀请码中包含 `secretKey`，访客拿到后用 frpc 以 XTCP visitor 模式反向连接
- frps 仅作为打洞中继，**不转发任何游戏流量**

## 常见问题

**Q: 房主和访客都需要公网 IP 吗？**
A: 不需要，只有 frps 服务端需要公网 IP。房主和访客都可以是 NAT/家庭网络。

**Q: 邀请码会泄露 frps 凭据吗？**
A: 不会。邀请码只包含 frps 地址、端口和临时 secretKey，不会泄露 frps 的 token。

**Q: 关闭游戏后 frpc 进程会残留吗？**
A: 不会。模组绑定了 `MinecraftClient.stop()` 事件，世界关闭 / 退出游戏时强制销毁 frpc 子进程。

**Q: 跨版本兼容吗？**
A: 当前仅针对 1.20.1 验证。其他版本需自行调整 `mappings` 和 `mixin` 注入点。

## 项目结构

```
src/main/java/com/mcfrp/
├── McFrpClient.java         # 模组入口
├── config/                  # mcfrp.properties 读写
├── frpc/                    # frpc 二进制、配置生成、子进程管理
├── host/                    # 房主侧 LAN 监听与邀请码生成
├── invite/                  # 邀请码编解码
├── mixin/                   # 原版界面的 mixin 注入
├── ui/                      # 自定义 GUI 屏幕
└── visitor/                 # 访客侧连接逻辑
```

## 依赖说明

- 运行时：Fabric Loader + fabric-api（用户安装）
- 构建期：fabric-loom（Gradle 插件）
- **无第三方运行时依赖**：JSON 解析使用模组自带的轻量实现，不引入 Gson

## 许可证

本模组源代码遵循仓库内 `LICENSE`。

本模组**包含** [frp](https://github.com/fatedier/frp) 的 Apache License 2.0 许可声明，完整许可文本见 `src/main/resources/LICENSE`。frpc 可执行文件不随本仓库分发，请从 frp 官方仓库下载。
