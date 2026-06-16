package com.mcfrp.visitor;

import com.mcfrp.McFrpClient;
import com.mcfrp.frpc.FrpcConfig;
import com.mcfrp.frpc.FrpcProcess;
import com.mcfrp.invite.InviteCode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.io.IOException;
import java.nio.file.Path;

public class VisitorManager {
    private static final int LOCAL_BIND_PORT = 25566;

    public static void connect(String inviteCode, MultiplayerScreen parent) {
        MinecraftClient client = MinecraftClient.getInstance();

        InviteCode.InviteData data;
        try {
            data = InviteCode.decode(inviteCode);
        } catch (Exception e) {
            McFrpClient.LOGGER.error("Failed to decode invite code: {}", inviteCode, e);
            return;
        }

        McFrpClient.LOGGER.info("Decoded invite code: secretKey={}..., frpsHost={}:{}",
            data.secretKey.substring(0, 8), data.frpsHost, data.frpsPort);

        Path configPath;
        try {
            configPath = FrpcConfig.writeVisitorConfig(
                data.secretKey,
                data.frpsHost,
                data.frpsPort,
                data.token,
                LOCAL_BIND_PORT
            );
        } catch (IOException e) {
            McFrpClient.LOGGER.error("Failed to write visitor frpc config", e);
            return;
        }

        try {
            FrpcProcess.getInstance().start(configPath);
        } catch (IOException e) {
            McFrpClient.LOGGER.error("Failed to start frpc", e);
            return;
        }

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        client.execute(() -> {
            ServerInfo info = new ServerInfo("mcfrp", "127.0.0.1:25566", false);
            ConnectScreen.connect(parent, client,
                ServerAddress.parse("127.0.0.1:25566"),
                info,
                false);
        });

        McFrpClient.LOGGER.info("Connecting to MC FRP P2P server via 127.0.0.1:25566");
    }
}
