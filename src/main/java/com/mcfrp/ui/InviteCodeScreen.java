package com.mcfrp.ui;

import com.mcfrp.visitor.VisitorManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class InviteCodeScreen extends Screen {
    private final MultiplayerScreen parent;
    private TextFieldWidget inviteCodeField;

    public InviteCodeScreen(MultiplayerScreen parent) {
        super(Text.literal("通过邀请码加入"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 200;
        int h = 20;
        int x = this.width / 2 - w / 2;

        // 重新布局：标题在上，格式提示在标题下，输入框在格式提示下，按钮组在最下方
        int titleY = this.height / 2 - 80;
        int hintY = this.height / 2 - 55;
        int fieldY = this.height / 2 - 10;
        int btn1Y = this.height / 2 + 25;
        int btn2Y = this.height / 2 + 50;

        inviteCodeField = new TextFieldWidget(
            this.textRenderer,
            x, fieldY,
            w, h,
            Text.literal("邀请码")
        );
        inviteCodeField.setMaxLength(256);
        inviteCodeField.setFocused(true);
        this.addSelectableChild(inviteCodeField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("确定"), button -> {
            String code = inviteCodeField.getText().trim();
            if (!code.isEmpty()) {
                this.client.setScreen(parent);
                VisitorManager.connect(code, parent);
            }
        }).dimensions(x, btn1Y, w, h).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("取消"), button -> {
            this.client.setScreen(parent);
        }).dimensions(x, btn2Y, w, h).build());
    }

    @Override
    public void tick() {
        inviteCodeField.tick();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inviteCodeField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == 256) {
            this.client.setScreen(parent);
            return true;
        }
        return false;
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 80, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("输入邀请码 (MCFRP://...)"), this.width / 2, this.height / 2 - 55, 0xAAAAAA);
        inviteCodeField.render(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }
}
