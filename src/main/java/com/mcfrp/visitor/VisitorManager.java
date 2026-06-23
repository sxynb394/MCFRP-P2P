package com.mcfrp.visitor;

import com.mcfrp.McFrpClient;
import com.mcfrp.frpc.FrpcConfig;
import com.mcfrp.frpc.FrpcProcess;
import com.mcfrp.manager.FrpsConfigManager;
import com.mcfrp.manager.P2PEntryManager;
import com.mcfrp.util.InviteCodeData;
import com.mcfrp.util.InviteCodeUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class VisitorManager {
    private static final int DEFAULT_BIND_PORT = 25566;
    private static final int HOLE_PUNCH_TIMEOUT = 30;

    public static boolean joinOnce(String rawInput, Screen parent) {
        return joinInternal(rawInput, parent, false, DEFAULT_BIND_PORT);
    }

    public static boolean joinOnce(String rawInput, Screen parent, int localPort) {
        return joinInternal(rawInput, parent, false, localPort);
    }

    public static boolean joinWithSave(String rawInput, Screen parent) {
        return joinInternal(rawInput, parent, true, DEFAULT_BIND_PORT);
    }

    public static boolean joinWithSave(String rawInput, Screen parent, int localPort) {
        return joinInternal(rawInput, parent, true, localPort);
    }

    private static boolean joinInternal(String rawInput, Screen parent, boolean save, int bindPort) {
        if (rawInput == null || rawInput.isEmpty()) return false;
        String code = rawInput.trim();

        if (!InviteCodeUtil.isInviteCode(code)) return false;

        MinecraftClient client = MinecraftClient.getInstance();

        InviteCodeData data;
        try {
            data = InviteCodeUtil.decode(code);
        } catch (IllegalArgumentException e) {
            McFrpClient.LOGGER.warn("[MCFRP] 解码失败", e);
            return false;
        }

        McFrpClient.LOGGER.info("[MCFRP] 邀请码解码成功: serverName={}, frps={}:{}",
            data.getServerName(), data.getFrpsAddr(), data.getFrpsPort());

        String frpcAuthToken = FrpsConfigManager.getInstance().getEffectiveToken();

        Path configPath;
        try {
            configPath = FrpcConfig.writeVisitorConfig(
                data.getSecretKey(),
                data.getFrpsAddr(),
                data.getFrpsPort(),
                frpcAuthToken,
                bindPort
            );
        } catch (IOException e) {
            McFrpClient.LOGGER.error("[MCFRP] 配置写入失败", e);
            writeToField(client, "✗ 写 frpc 配置失败");
            return true;
        }

        try {
            FrpcProcess.getInstance().start(configPath);
        } catch (IOException e) {
            McFrpClient.LOGGER.error("[MCFRP] 进程启动失败", e);
            writeToField(client, "✗ frpc 启动失败");
            return true;
        }

        final String finalServerName = data.getServerName();
        final boolean shouldSave = save;
        final String finalFrpsAddr = data.getFrpsAddr();
        final int finalFrpsPort = data.getFrpsPort();
        final String finalSecretKey = data.getSecretKey();

        Thread connectThread = new Thread(() -> {
            boolean ok = FrpcProcess.getInstance().waitForLocalPort(bindPort, HOLE_PUNCH_TIMEOUT);
            if (!ok) {
                FrpcProcess.getInstance().stop();
                String err = FrpcProcess.getInstance().drainErrorFromLogs();
                if (err != null && err.toLowerCase().contains("doesn't exist")) {
                    McFrpClient.LOGGER.error("[MCFRP] 房主不在线: " + err);
                    writeToField(client, "✗ 房主不在线，请等待房主先启动联机");
                } else if (err != null) {
                    McFrpClient.LOGGER.error("[MCFRP] 打洞失败: " + err);
                    writeToField(client, "✗ 连接 frps 失败，请检查地址");
                } else {
                    McFrpClient.LOGGER.error("[MCFRP] 打洞失败: 超时");
                    writeToField(client, "✗ 打洞超时，请检查网络");
                }
                return;
            }

            if (shouldSave) {
                try {
                    P2PEntryManager.getInstance().saveEntry(
                        finalServerName,
                        finalFrpsAddr,
                        finalFrpsPort,
                        finalSecretKey
                    );
                } catch (Exception e) {
                    McFrpClient.LOGGER.warn("[MCFRP] 持久化失败", e);
                }
            }

            final int finalBindPort = bindPort;
            client.execute(() -> {
                String addrString = "127.0.0.1:" + finalBindPort;
                ServerInfo info = new ServerInfo(
                    finalServerName == null || finalServerName.isEmpty()
                        ? "mcfrp" : finalServerName,
                    addrString,
                    false
                );
                ConnectScreen.connect(parent, client,
                    ServerAddress.parse(addrString),
                    info, false);
            });
        }, "mcfrp-connect");
        connectThread.setDaemon(true);
        connectThread.start();

        return true;
    }

    private static void writeToField(MinecraftClient client, String message) {
        client.execute(() -> {
            Screen screen = client.currentScreen;
            if (screen == null) return;
            TextFieldWidget field = findTextField(screen);
            if (field != null) {
                field.setMaxLength(256);
                field.setText(message);
                field.setCursorToEnd();
                field.setSelectionStart(0);
                field.setEditableColor(0xFFFF5555);
            }
        });
    }

    private static TextFieldWidget findTextField(Screen screen) {
        try {
            List<Object> all = new ArrayList<>();
            collectAllChildren(screen, all);
            for (Object obj : all) {
                if (obj instanceof TextFieldWidget tf) return tf;
            }
        } catch (Throwable ignore) {}
        return null;
    }

    private static void collectAllChildren(ParentElement parent, List<Object> out) {
        for (Element child : parent.children()) {
            if (child != null) {
                out.add(child);
                if (child instanceof ParentElement pe) {
                    collectAllChildren(pe, out);
                }
            }
        }
    }
}
