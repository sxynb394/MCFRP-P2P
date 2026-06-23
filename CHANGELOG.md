# Changelog

## v0.2.0 (2026-06-18)

### 新增
- 房主自动启动 frpc：单人世界"对局域网开放"后自动启动 frpc、生成邀请码并复制到剪贴板
- 联机控制面板（FrpcControlScreen）：ESC 菜单新增"联机管理"按钮，可查看 frpc 状态、复制/刷新邀请码、重启/关闭联机
- 聊天框可点击重启：frpc 启动失败时聊天栏显示可点击的重启提示
- `/mcfrp restart` 命令：聊天框输入或点击执行，重新启动 frpc
- 自定义 FRPS 服务器：多人游戏界面新增"P2P 设置"，可配置自定义 frps 地址、端口和 Token
- 直接连接支持邀请码：在"直接连接"或"添加服务器"界面输入 MCFRP:// 邀请码即可加入 P2P 房间
- P2P 条目持久化：加入的房间自动保存在原版服务器列表中，地址为 `127.0.0.1:25566`，名称前缀 `[P2P]`
- P2P 标记渲染：服务器列表中 P2P 条目右上角显示紫色 "P2P" 标记
- 邀请码格式校验：输入错误邀请码时显示红色错误提示（InviteCodeScreen / 直接连接 / 添加服务器）
- FRPS 配置校验：自定义 frps 时校验地址格式和端口范围，不合法阻止保存
- FRPS 连接校验：房主启动后等待 5 秒检测 frpc 是否成功连接 FRPS，失败则杀进程弹提示
- 日志脱敏：所有 token、secretKey 在日志中替换为 `[REDACTED]`；frpc 输出行正则过滤
- 日志格式化：统一 `[MCFRP] 错误类型: 详细描述` 格式

### 修复
- 单人游戏 ESC 菜单"联机管理"按钮在开服后消失的问题
- 联机管理面板 FAILED 状态下文字与按钮重叠
- 邀请码解码失败时不保存为普通服务器条目

### 变更
- HostManager 由静态工具类改为单例模式，暴露状态查询和控制接口

---

## v0.1.1

### 修复
- 邀请码格式从 JSON 文本改为二进制 Base64 压缩格式，缩短长度
- 邀请码预检查增加长度和字符集校验，防止无效码拦截 vanilla 行为
- VisitManager.joinWithSave 改为仅在打洞成功后保存条目

---

## v0.1.0

### 初始版本
- frpc 二进制解包和进程管理（FrpcBinary / FrpcProcess）
- 房主 frpc-host.toml 配置生成
- 访客 frpc-visitor.toml 配置生成
- InviteCodeUtil 编码/解码（Base64 二进制格式）
- HostManager 基础启动流程
- VisitorManager 基础连接流程
- "通过邀请码加入"独立界面（InviteCodeScreen）
- 多人游戏界面按钮注入（MultiplayerScreenMixin）
- 局域网开放拦截（IntegratedServerMixin）
