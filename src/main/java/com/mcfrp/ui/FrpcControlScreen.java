package com.mcfrp.ui;

import com.mcfrp.host.HostManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class FrpcControlScreen extends Screen {
    private static final int BTN_W = 120;

    private final Screen parent;
    private int centerX;
    private int baseY;
    private HostManager.Status lastStatus;

    public FrpcControlScreen(Screen parent) {
        super(Text.literal("联机管理"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearChildren();
        HostManager host = HostManager.getInstance();
        this.lastStatus = host.getStatus();

        centerX = this.width / 2;
        baseY = this.height / 2 - 64;

        switch (lastStatus) {
            case IDLE:
                addDrawableChild(ButtonWidget.builder(
                    Text.literal("重新启动联机"),
                    btn -> host.restart()
                ).dimensions(centerX - BTN_W - 6, baseY + 86, BTN_W, 20).build());
                addDrawableChild(ButtonWidget.builder(
                    Text.literal("返回"),
                    btn -> returnToParent()
                ).dimensions(centerX + 6, baseY + 86, BTN_W, 20).build());
                addDrawableChild(ButtonWidget.builder(
                    Text.literal("P2P 设置"),
                    btn -> openFrpsConfig()
                ).dimensions(centerX - 50, baseY + 112, 100, 20).build());
                break;

            case STARTING:
                addDrawableChild(ButtonWidget.builder(
                    Text.literal("返回"),
                    btn -> returnToParent()
                ).dimensions(centerX - 50, baseY + 86, 100, 20).build());
                addDrawableChild(ButtonWidget.builder(
                    Text.literal("P2P 设置"),
                    btn -> openFrpsConfig()
                ).dimensions(centerX - 50, baseY + 112, 100, 20).build());
                break;

            case RUNNING:
                String code = host.getInviteCode();
                addDrawableChild(ButtonWidget.builder(
                    Text.literal("复制邀请码"),
                    btn -> host.copyToClipboard(code)
                ).dimensions(centerX - BTN_W - 6, baseY + 76, BTN_W, 20).build());
                addDrawableChild(ButtonWidget.builder(
                    Text.literal("刷新邀请码"),
                    btn -> host.refreshInviteCode()
                ).dimensions(centerX + 6, baseY + 76, BTN_W, 20).build());
                addDrawableChild(ButtonWidget.builder(
                    Text.literal("关闭联机"),
                    btn -> host.stop()
                ).dimensions(centerX - BTN_W - 6, baseY + 102, BTN_W, 20).build());
                addDrawableChild(ButtonWidget.builder(
                    Text.literal("返回"),
                    btn -> returnToParent()
                ).dimensions(centerX + 6, baseY + 102, BTN_W, 20).build());
                addDrawableChild(ButtonWidget.builder(
                    Text.literal("P2P 设置"),
                    btn -> openFrpsConfig()
                ).dimensions(centerX - 50, baseY + 128, 100, 20).build());
                break;

            case FAILED:
                addDrawableChild(ButtonWidget.builder(
                    Text.literal("重新尝试"),
                    btn -> host.restart()
                ).dimensions(centerX - BTN_W - 6, baseY + 86, BTN_W, 20).build());
                addDrawableChild(ButtonWidget.builder(
                    Text.literal("返回"),
                    btn -> returnToParent()
                ).dimensions(centerX + 6, baseY + 86, BTN_W, 20).build());
                addDrawableChild(ButtonWidget.builder(
                    Text.literal("P2P 设置"),
                    btn -> openFrpsConfig()
                ).dimensions(centerX - 50, baseY + 112, 100, 20).build());
                break;
        }
    }

    @Override
    public void tick() {
        HostManager host = HostManager.getInstance();
        HostManager.Status current = host.getStatus();
        if (current != lastStatus) {
            init();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 15, 0xFFFFFF);

        HostManager host = HostManager.getInstance();
        HostManager.Status status = host.getStatus();

        String statusText;
        int statusColor;
        switch (status) {
            case IDLE:
                statusText = "● 未启动";
                statusColor = 0xFFAAAAAA;
                break;
            case STARTING:
                statusText = "● 启动中...";
                statusColor = 0xFFCCAA00;
                break;
            case RUNNING:
                statusText = "● 运行中";
                statusColor = 0xFF55FF55;
                break;
            case FAILED:
                statusText = "● 启动失败";
                statusColor = 0xFFFF5555;
                break;
            default:
                statusText = "● 未知";
                statusColor = 0xFFAAAAAA;
        }
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusText), centerX, baseY, statusColor);

        int line = baseY + 20;
        switch (status) {
            case IDLE:
                context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("联机未开启，请使用 [对局域网开放] 启动"),
                    centerX, line, 0xFFAAAAAA);
                context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("或点击下方按钮重新启动"),
                    centerX, line + 14, 0xFFAAAAAA);
                break;

            case STARTING:
                context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("正在启动 frpc 并连接服务器..."),
                    centerX, line, 0xFFCCAA00);
                break;

            case RUNNING:
                String server = host.getServerName();
                context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("邀请码已生成，可点击下方按钮复制"),
                    centerX, line, 0xFF55FFFF);
                context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("房间: " + (server != null ? server : "-")),
                    centerX, line + 14, 0xFFAAAAAA);
                break;

            case FAILED:
                String err = host.getLastError();
                String displayErr = (err != null && !err.isEmpty())
                    ? err : "未知错误";
                if (displayErr.length() > 50) displayErr = displayErr.substring(0, 48) + "...";
                context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("错误原因：" + displayErr), centerX, line, 0xFFFF5555);
                context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("请点击重新尝试或检查配置后重试"),
                    centerX, line + 14, 0xFFAAAAAA);
                break;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void openFrpsConfig() {
        if (this.client != null) {
            this.client.setScreen(new FrpsConfigScreen(this));
        }
    }

    private void returnToParent() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
