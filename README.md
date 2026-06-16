# MCFRP-P2P 模组公开源码

这是 MCFRP-P2P 模组公开源码。

## 构建

```bash
./gradlew build
```

Windows 下也可以使用：

```bat
gradlew.bat build
```

## 使用说明

使用前需在 `.minecraft/config/mcfrp.properties` 中配置 FRPS 服务器。

示例：

```properties
frps.host=your.frps.server
frps.port=7000
frps.token=your_frps_token
```

`frpc` 二进制需自行从 https://github.com/fatedier/frp/releases 下载，并按源码中的资源路径放置。

本模组包含 frp（Apache License 2.0）。
