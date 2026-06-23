package com.mcfrp.mixin;

import com.mcfrp.McFrpClient;
import com.mcfrp.frpc.FrpcConfig;
import com.mcfrp.frpc.FrpcProcess;
import com.mcfrp.manager.FrpsConfigManager;
import com.mcfrp.manager.P2PEntryManager;
import com.mcfrp.util.SimpleJson;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.net.Socket;

@Mixin(MultiplayerServerListWidget.ServerEntry.class)
public abstract class MultiplayerServerListEntryMixin {

    @Unique private int mcfrp$pingBtnX;
    @Unique private int mcfrp$pingBtnY;
    @Unique private int mcfrp$pingBtnW;
    @Unique private int mcfrp$pingBtnH;
    @Unique private boolean mcfrp$hasPingBtn;
    @Unique private SimpleJson.P2PEntry mcfrp$cachedEntry;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderEntryHead(DrawContext context, int index, int y, int x,
                               int entryWidth, int entryHeight,
                               int mouseX, int mouseY, boolean hovered, float delta,
                               CallbackInfo ci) {
        try {
            MultiplayerServerListWidget.ServerEntry self =
                (MultiplayerServerListWidget.ServerEntry) (Object) this;
            if (self.getServer() == null) {
                ci.cancel();
            }
        } catch (Throwable t) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderEntry(DrawContext context, int index, int y, int x,
                               int entryWidth, int entryHeight,
                               int mouseX, int mouseY, boolean hovered, float delta,
                               CallbackInfo ci) {
        try {
            MultiplayerServerListWidget.ServerEntry self =
                (MultiplayerServerListWidget.ServerEntry) (Object) this;
            ServerInfo info = self.getServer();
            if (info == null || info.name == null) return;

            SimpleJson.P2PEntry p2p = P2PEntryManager.getInstance().findByServerName(info.name);
            if (p2p == null) {
                mcfrp$hasPingBtn = false;
                return;
            }
            mcfrp$cachedEntry = p2p;

            if (info.label != null) {
                String ls = info.label.getString();
                if (ls.contains("无法连接") || ls.contains("Can't connect")
                    || ls.contains("Can't resolve") || ls.contains("Unknown host")
                    || ls.contains("connect timed out") || ls.contains("Connection refused")
                    || ls.contains("No route to host")) {
                    info.label = Text.empty();
                }
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return;
            TextRenderer tr = client.textRenderer;

            Text p2pLabel = Text.literal("\u26A1");
            int p2pW = tr.getWidth(p2pLabel);
            int p2pX = x + 3;
            int p2pY = y + 2;
            context.drawTextWithShadow(tr, p2pLabel, p2pX, p2pY, 0xFFB388FF);

            int btnW = 36;
            int btnH = 14;
            int btnX = x + entryWidth - btnW - 2;
            int btnY = y + 14;

            mcfrp$pingBtnX = btnX;
            mcfrp$pingBtnY = btnY;
            mcfrp$pingBtnW = btnW;
            mcfrp$pingBtnH = btnH;
            mcfrp$hasPingBtn = true;

            boolean hovering = mouseX >= btnX && mouseX <= btnX + btnW
                            && mouseY >= btnY && mouseY <= btnY + btnH;

            context.fill(btnX, btnY, btnX + btnW, btnY + btnH, 0x70000000);
            context.drawBorder(btnX, btnY, btnW, btnH,
                hovering ? 0xFFFFFFFF : 0xFFA0A0A0);
            context.drawHorizontalLine(btnX + 1, btnX + btnW - 2, btnY + 1, 0x40FFFFFF);
            context.drawHorizontalLine(btnX + 1, btnX + btnW - 2, btnY + 2, 0x20FFFFFF);

            Text pingLabel = Text.literal("测速");
            int textW = tr.getWidth(pingLabel);
            context.drawTextWithShadow(tr, pingLabel,
                btnX + (btnW - textW) / 2, btnY + (btnH - 8) / 2, 0xFFE0E0E0);
        } catch (Throwable t) {
            McFrpClient.LOGGER.warn("[MCFRP] P2P 渲染异常", t);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button,
                                CallbackInfoReturnable<Boolean> cir) {
        if (!mcfrp$hasPingBtn) return;
        if (button != 0) return;
        if (mouseX < mcfrp$pingBtnX || mouseX > mcfrp$pingBtnX + mcfrp$pingBtnW) return;
        if (mouseY < mcfrp$pingBtnY || mouseY > mcfrp$pingBtnY + mcfrp$pingBtnH) return;

        cir.cancel();
        cir.setReturnValue(true);

        SimpleJson.P2PEntry p2p = mcfrp$cachedEntry;
        if (p2p == null) return;

        final int pingPort = p2p.localPort > 0 ? p2p.localPort : 25566;
        final String secretKey = p2p.secretKey;
        final String frpsAddr = p2p.frpsAddr;
        final int frpsPort = p2p.frpsPort;

        MultiplayerServerListWidget.ServerEntry self =
            (MultiplayerServerListWidget.ServerEntry) (Object) this;
        ServerInfo info = self.getServer();
        final MinecraftClient client = MinecraftClient.getInstance();

        new Thread(() -> {
            try {
                String token = FrpsConfigManager.getInstance().getEffectiveToken();
                java.nio.file.Path configPath = FrpcConfig.writeVisitorConfig(
                    secretKey, frpsAddr, frpsPort, token, pingPort);
                FrpcProcess.getInstance().start(configPath);
            } catch (Exception e) {
                McFrpClient.LOGGER.warn("[MCFRP] 测速启动frpc失败", e);
                client.execute(() -> { if (info != null) info.ping = -1L; });
                return;
            }

            boolean ready = FrpcProcess.getInstance().waitForLocalPort(pingPort, 10);
            long latency = -1L;
            if (ready) {
                long start = System.currentTimeMillis();
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress("127.0.0.1", pingPort), 2000);
                    latency = System.currentTimeMillis() - start;
                } catch (Exception ignored) {}
            }

            final long result = latency;
            client.execute(() -> {
                if (info != null) {
                    info.ping = result;
                    if (result > 0) info.label = Text.empty();
                }
            });
        }, "mcfrp-ping").start();
    }
}
