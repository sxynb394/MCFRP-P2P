package com.mcfrp.host;

import com.mcfrp.McFrpClient;
import com.mcfrp.config.McFrpConfig;
import com.mcfrp.frpc.FrpcConfig;
import com.mcfrp.frpc.FrpcProcess;
import com.mcfrp.invite.InviteCode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Path;

public class HostManager {
    public static void start(int lanPort) {
        if (!McFrpConfig.isEnabled()) {
            McFrpClient.LOGGER.error("MC FRP is not enabled (config missing or invalid)");
            return;
        }

        start(lanPort, McFrpConfig.instance.frpsHost, McFrpConfig.instance.frpsPort, McFrpConfig.instance.frpsToken);
    }

    public static void start(int lanPort, String frpsHost, int frpsPort, String frpsToken) {
        MinecraftClient client = MinecraftClient.getInstance();

        String secretKey = InviteCode.generateSecretKey();
        McFrpClient.LOGGER.info("Generated secret key: {}", secretKey.substring(0, 8) + "...");

        Path configPath;
        try {
            configPath = FrpcConfig.writeHostConfig(secretKey, frpsHost, frpsPort, frpsToken, lanPort);
        } catch (IOException e) {
            McFrpClient.LOGGER.error("Failed to write host frpc config", e);
            return;
        }

        try {
            FrpcProcess.getInstance().start(configPath);
        } catch (IOException e) {
            McFrpClient.LOGGER.error("Failed to start frpc", e);
            return;
        }

        String inviteCode = InviteCode.encode(
            secretKey,
            frpsHost,
            frpsPort,
            frpsToken
        );

        client.execute(() -> {
            MutableText msg = Text.literal("[MCFRP] 邀请码已生成，点击复制 → ")
                .append(Text.literal("[复制]")
                    .styled(s -> s
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, inviteCode))
                        .withColor(Formatting.GREEN)
                        .withUnderline(true)));
            if (client.player != null) {
                client.player.sendMessage(msg, false);
            }
        });

        McFrpClient.LOGGER.info("Host started with invite code: {}", inviteCode);
    }
}
