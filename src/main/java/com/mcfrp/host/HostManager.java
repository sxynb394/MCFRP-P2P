package com.mcfrp.host;

import com.mcfrp.McFrpClient;
import com.mcfrp.frpc.FrpcConfig;
import com.mcfrp.frpc.FrpcProcess;
import com.mcfrp.manager.FrpsConfigManager;
import com.mcfrp.util.InviteCodeData;
import com.mcfrp.util.InviteCodeUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.io.IOException;

public class HostManager {

    public enum Status { IDLE, STARTING, RUNNING, FAILED }

    public enum ErrorType {
        NONE,
        BINARY_NOT_FOUND,
        CONFIG_INVALID,
        PROCESS_LAUNCH_FAILED,
        TIMEOUT
    }

    private static final HostManager INSTANCE = new HostManager();

    public static HostManager getInstance() { return INSTANCE; }

    private Status status = Status.IDLE;
    private ErrorType errorType = ErrorType.NONE;
    private String lastErrorDetail = "";
    private int restartCount = 0;

    private long startId = 0;

    private int lanPort;
    private String frpsAddr;
    private int frpsPort;
    private String token;
    private String secretKey;
    private String inviteCode;
    private String serverName;

    private HostManager() {}

    public synchronized Status getStatus() { return status; }
    public synchronized String getInviteCode() { return inviteCode; }
    public synchronized String getServerName() { return serverName; }
    public synchronized String getFrpsAddr() { return frpsAddr; }
    public synchronized int getFrpsPort() { return frpsPort; }
    public synchronized String getLastError() { return lastErrorDetail; }
    public synchronized ErrorType getErrorType() { return errorType; }

    public synchronized void start(int lanPort) {
        this.lanPort = lanPort;
        FrpsConfigManager cfg = FrpsConfigManager.getInstance();
        this.frpsAddr = cfg.getFrpsAddr();
        this.frpsPort = cfg.getFrpsPort();
        this.token = cfg.getEffectiveToken();

        this.startId++;
        setStatus(Status.STARTING, ErrorType.NONE, "");

        long capturedStartId = this.startId;
        Thread t = new Thread(() -> doStart(capturedStartId), "mcfrp-host-start");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void stop() {
        startId++; // 使正在执行的 doStart 失效
        FrpcProcess.getInstance().stop();
        clearRunningState();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.execute(() -> client.player.sendMessage(
                Text.literal("[MCFRP] 联机已关闭"), false));
        }
    }

    public synchronized void restart() {
        if (status == Status.STARTING) return;
        int savedRetryCount = ++restartCount;
        stop();
        start(lanPort);
        this.restartCount = savedRetryCount;
        sendChatMsg("[MCFRP] 正在重新启动 frpc... (第 " + restartCount + " 次重试)");
    }

    public synchronized void refreshInviteCode() {
        if (status != Status.RUNNING) return;
        String newSecretKey = InviteCodeUtil.generateSecretKey();

        java.nio.file.Path configPath;
        try {
            configPath = FrpcConfig.writeHostConfig(newSecretKey, frpsAddr, frpsPort, token, lanPort);
        } catch (IOException e) {
            McFrpClient.LOGGER.error("[MCFRP] 刷新邀请码失败: 无法写入 frpc 配置", e);
            sendChatMsg("[MCFRP] 刷新邀请码失败，请检查日志");
            return;
        }

        this.secretKey = newSecretKey;
        InviteCodeData data = new InviteCodeData(frpsAddr, frpsPort, newSecretKey, serverName);
        try {
            this.inviteCode = InviteCodeUtil.encode(data);
        } catch (Exception e) {
            McFrpClient.LOGGER.error("[MCFRP] 编码失败: 无法生成邀请码", e);
            sendChatMsg("[MCFRP] 邀请码生成失败");
            return;
        }

        // 用新配置重启 frpc（不改变运行状态）
        FrpcProcess.getInstance().stop();
        try {
            FrpcProcess.getInstance().start(configPath);
        } catch (IOException e) {
            McFrpClient.LOGGER.error("[MCFRP] 刷新邀请码失败: frpc 重启失败", e);
            sendChatMsg("[MCFRP] 刷新邀请码失败，请检查日志");
            return;
        }

        McFrpClient.LOGGER.info("[MCFRP] 邀请码已刷新 (masked)");
        showInviteCode(inviteCode, "[MCFRP] 邀请码已刷新并复制到剪贴板");
    }

    public synchronized void copyToClipboard(String code) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.keyboard != null && code != null) {
            try {
                client.keyboard.setClipboard(code);
            } catch (Throwable e) {
                McFrpClient.LOGGER.warn("[MCFRP] 剪贴板写入失败", e);
            }
        }
    }

    // ─── 内部逻辑 ───

    private void doStart(long myStartId) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (!checkStartId(myStartId)) return;

        secretKey = InviteCodeUtil.generateSecretKey();
        String shortId = secretKey.substring(0, Math.min(8, secretKey.length()));

        McFrpClient.LOGGER.info("[MCFRP] 房主启动: frps={}:{}, keyPrefix={}...",
            frpsAddr, frpsPort, shortId);

        // 写 frpc-host.toml
        java.nio.file.Path configPath;
        try {
            configPath = FrpcConfig.writeHostConfig(secretKey, frpsAddr, frpsPort, token, lanPort);
        } catch (IOException e) {
            McFrpClient.LOGGER.error("[MCFRP] 配置写入失败: 无法写入 frpc-host.toml", e);
            failIfCurrent(myStartId, ErrorType.CONFIG_INVALID, "配置文件生成失败");
            return;
        }

        if (!checkStartId(myStartId)) return;

        // 启动 frpc 进程
        try {
            FrpcProcess.getInstance().start(configPath);
        } catch (IOException e) {
            McFrpClient.LOGGER.error("[MCFRP] 进程启动失败: 无法启动 frpc 进程", e);
            String detail = e.getMessage();
            ErrorType et = (detail != null && detail.contains("Frpc binary not found"))
                ? ErrorType.BINARY_NOT_FOUND : ErrorType.PROCESS_LAUNCH_FAILED;
            failIfCurrent(myStartId, et, et == ErrorType.BINARY_NOT_FOUND
                ? "frpc 二进制文件缺失" : "进程启动失败");
            return;
        }

        if (!checkStartId(myStartId)) {
            FrpcProcess.getInstance().stop();
            return;
        }

        // 等待连接验证
        client.execute(() -> sendChatMsg("[MCFRP] 正在连接 P2P 服务器 " + frpsAddr + ":" + frpsPort + " ..."));
        String connError = FrpcProcess.getInstance().waitForConnection(5);
        if (!checkStartId(myStartId)) {
            FrpcProcess.getInstance().stop();
            return;
        }
        if (connError != null) {
            FrpcProcess.getInstance().stop();
            McFrpClient.LOGGER.error("[MCFRP] 连接失败: frpc 无法连接 FRPS {}:{}, 原因: {}",
                frpsAddr, frpsPort, connError);
            String reason = connError.length() > 60 ? connError.substring(0, 60) + "..." : connError;
            failIfCurrent(myStartId, ErrorType.TIMEOUT, "连接服务器失败 - " + reason);
            return;
        }

        // 服务器名
        String resolved = resolveServerName(client);

        if (!checkStartId(myStartId)) {
            FrpcProcess.getInstance().stop();
            return;
        }
        synchronized (this) { serverName = resolved; }

        // 生成邀请码
        InviteCodeData data = new InviteCodeData(frpsAddr, frpsPort, secretKey, serverName);
        String code;
        try {
            code = InviteCodeUtil.encode(data);
        } catch (Exception e) {
            McFrpClient.LOGGER.error("[MCFRP] 编码失败: 无法生成邀请码", e);
            failIfCurrent(myStartId, ErrorType.CONFIG_INVALID, "邀请码生成失败");
            return;
        }

        synchronized (this) {
            if (status != Status.STARTING) return; // 已被 stop
            this.inviteCode = code;
            setStatus(Status.RUNNING, ErrorType.NONE, "");
            restartCount = 0;
        }

        McFrpClient.LOGGER.info("[MCFRP] 邀请码已生成 (masked)");

        showInviteCode(code, "[MCFRP] 房间：" + serverName
            + " | frps: " + frpsAddr + ":" + frpsPort + " | 邀请码已复制到剪贴板");
    }

    private void showInviteCode(String code, String headerMsg) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        String finalCode = code;
        client.execute(() -> {
            copyToClipboard(finalCode);
            if (client.player == null) return;
            client.player.sendMessage(Text.literal(headerMsg), false);
            client.player.sendMessage(Text.literal("[MCFRP] " + finalCode), false);
        });
    }

    private boolean checkStartId(long myStartId) {
        return this.startId == myStartId;
    }

    private void failIfCurrent(long myStartId, ErrorType type, String reason) {
        if (!checkStartId(myStartId)) return;
        onStartFailed(type, reason);
    }

    private void onStartFailed(ErrorType type, String reason) {
        setStatus(Status.FAILED, type, reason);
        McFrpClient.LOGGER.error("[MCFRP] 启动失败: {} ({})", reason, type);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            if (client.player == null) return;
            String shortReason = reason.length() > 40 ? reason.substring(0, 40) + "..." : reason;
            client.player.sendMessage(Text.literal("[MCFRP] frpc 启动失败：" + shortReason), false);

            Style clickStyle = Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mcfrp restart"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Text.literal("点击重新启动 frpc")))
                .withUnderline(true);
            client.player.sendMessage(
                Text.literal("[MCFRP] ").append(
                    Text.literal("点击此处重新尝试 (已重试 " + restartCount + " 次)").setStyle(clickStyle)
                ), false);
        });
    }

    private synchronized void clearRunningState() {
        setStatus(Status.IDLE, ErrorType.NONE, "");
        inviteCode = null;
        secretKey = null;
        restartCount = 0;
    }

    private synchronized void setStatus(Status s, ErrorType t, String err) {
        this.status = s;
        this.errorType = t;
        this.lastErrorDetail = err;
    }

    private static String resolveServerName(MinecraftClient client) {
        try {
            if (client.getServer() != null && client.getServer().getSaveProperties() != null) {
                String levelName = client.getServer().getSaveProperties().getLevelName();
                if (levelName != null && !levelName.isEmpty()) return levelName;
            }
        } catch (Exception e) {
            McFrpClient.LOGGER.warn("[MCFRP] 获取存档名失败，使用默认名称", e);
        }
        return "我的世界";
    }

    private static void sendChatMsg(String msg) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal(msg), false);
                }
            });
        }
    }
}
