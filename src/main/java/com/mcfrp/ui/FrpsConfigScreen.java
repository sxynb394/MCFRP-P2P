package com.mcfrp.ui;

import com.mcfrp.manager.FrpsConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class FrpsConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget addressField;
    private TextFieldWidget portField;
    private TextFieldWidget tokenField;
    private ButtonWidget modeButton;

    private FrpsConfigManager cfg;
    private boolean useCustom;
    private boolean dirty = false;
    private String errorMessage = null;
    private String successMessage = null;
    private int successTicks = 0;
    private static final int SUCCESS_DISPLAY_TICKS = 40;

    private int modeLabelY, modeBtnY;
    private int addrLabelY, addrFieldY;
    private int portLabelY, portFieldY;
    private int tokenLabelY, tokenFieldY;
    private int saveBtnY, cancelBtnY, hintY;

    public FrpsConfigScreen(Screen parent) {
        super(Text.literal("P2P 服务器设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cfg = FrpsConfigManager.getInstance();
        useCustom = (cfg.getMode() == FrpsConfigManager.Mode.CUSTOM);

        int w = 260;
        int halfW = w / 2;
        int cx = this.width / 2;
        int startY = this.height / 2 - 95;
        int labelGap = 12;
        int fieldGap = 6;
        int sectionGap = 10;

        modeLabelY = startY;
        modeBtnY = modeLabelY + labelGap;

        addrLabelY = modeBtnY + 20 + fieldGap;
        addrFieldY = addrLabelY + labelGap;

        portLabelY = addrFieldY + 20 + sectionGap;
        portFieldY = portLabelY + labelGap;

        tokenLabelY = portFieldY + 20 + sectionGap;
        tokenFieldY = tokenLabelY + labelGap;

        saveBtnY = tokenFieldY + 20 + 10;
        cancelBtnY = saveBtnY + 24;
        hintY = this.height - 12;

        modeButton = ButtonWidget.builder(
            Text.literal(useCustom ? "模式：自定义 frps" : "模式：默认 frps"),
            btn -> {
                useCustom = !useCustom;
                btn.setMessage(Text.literal(useCustom ? "模式：自定义 frps" : "模式：默认 frps"));
                if (useCustom) {
                    tokenField.setText("");
                } else {
                    tokenField.setText(cfg.getToken());
                }
                updateFieldsEnabled();
                dirty = true;
                successMessage = null;
            }
        ).dimensions(cx - halfW, modeBtnY, w, 20).build();
        this.addDrawableChild(modeButton);

        addressField = new TextFieldWidget(
            this.textRenderer, cx - halfW, addrFieldY, w, 20, Text.literal("服务器地址"));
        addressField.setText(cfg.getCustomAddr());
        addressField.setChangedListener(s -> { dirty = true; successMessage = null; });
        this.addSelectableChild(addressField);

        portField = new TextFieldWidget(
            this.textRenderer, cx - halfW, portFieldY, w, 20, Text.literal("服务器端口"));
        portField.setText(String.valueOf(cfg.getCustomPort()));
        portField.setChangedListener(s -> { dirty = true; successMessage = null; });
        this.addSelectableChild(portField);

        tokenField = new TextFieldWidget(
            this.textRenderer, cx - halfW, tokenFieldY, w, 20, Text.literal("Token"));
        tokenField.setText(cfg.getToken());
        tokenField.setChangedListener(s -> { dirty = true; successMessage = null; });
        this.addSelectableChild(tokenField);

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("保存并返回"),
            btn -> {
                if (saveConfig()) {
                    successMessage = "配置已保存";
                    successTicks = 0;
                }
            }
        ).dimensions(cx - halfW, saveBtnY, w, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("取消"),
            btn -> { if (this.client != null) this.client.setScreen(parent); }
        ).dimensions(cx - halfW, cancelBtnY, w, 20).build());

        updateFieldsEnabled();
    }

    private void updateFieldsEnabled() {
        addressField.setEditable(useCustom);
        addressField.setFocusUnlocked(useCustom);
        portField.setEditable(useCustom);
        portField.setFocusUnlocked(useCustom);
    }

    private boolean saveConfig() {
        errorMessage = null;

        String addr = addressField.getText() == null ? "" : addressField.getText().trim();
        String portStr = portField.getText() == null ? "" : portField.getText().trim();
        String token = tokenField.getText() == null ? "" : tokenField.getText().trim();

        if (useCustom) {
            if (addr.isEmpty()) { errorMessage = "服务器地址不能为空"; return false; }
            if (!isValidAddress(addr)) { errorMessage = "服务器地址格式不合法（需为 IP 或域名）"; return false; }
            if (portStr.isEmpty()) { errorMessage = "服务器端口不能为空"; return false; }
            int port;
            try { port = Integer.parseInt(portStr); } catch (NumberFormatException e) { errorMessage = "服务器端口必须为数字"; return false; }
            if (port < 1 || port > 65535) { errorMessage = "服务器端口范围不合法（1-65535）"; return false; }
        }

        cfg.setMode(useCustom ? FrpsConfigManager.Mode.CUSTOM : FrpsConfigManager.Mode.DEFAULT);
        cfg.setCustomAddr(addr);

        if (useCustom) {
            cfg.setCustomPort(Integer.parseInt(portStr));
        } else {
            try { cfg.setCustomPort(Integer.parseInt(portStr.isEmpty() ? "7000" : portStr)); } catch (NumberFormatException e) { cfg.setCustomPort(7000); }
        }

        cfg.setToken(token);
        cfg.save();
        dirty = false;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("[MCFRP] P2P 配置已保存: " + cfg.getFrpsAddr() + ":" + cfg.getFrpsPort()), false);
        }
        return true;
    }

    @Override
    public void tick() {
        addressField.tick();
        portField.tick();
        tokenField.tick();
        if (successMessage != null) {
            successTicks++;
            if (successTicks >= SUCCESS_DISPLAY_TICKS) successMessage = null;
        }
    }

    private static boolean isValidAddress(String addr) {
        if (addr == null || addr.isEmpty()) return false;
        if (addr.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
            for (String p : addr.split("\\.")) {
                int n = Integer.parseInt(p);
                if (n < 0 || n > 255) return false;
            }
            return true;
        }
        return addr.matches("^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)+$");
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && this.client != null && !dirty) {
            this.client.setScreen(parent);
            return true;
        }
        if (addressField.keyPressed(keyCode, scanCode, modifiers) || portField.keyPressed(keyCode, scanCode, modifiers) || tokenField.keyPressed(keyCode, scanCode, modifiers))
            return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);

        int lx = this.width / 2 - 130;
        context.drawTextWithShadow(this.textRenderer, "模式（点击切换）", lx, modeLabelY, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, "服务器地址（仅自定义模式）", lx, addrLabelY, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, "服务器端口（仅自定义模式）", lx, portLabelY, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, "Token（房主与访客需一致）", lx, tokenLabelY, 0xA0A0A0);

        if (successMessage != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(successMessage),
                this.width / 2, cancelBtnY + 20 + 10, 0xFF55FF55);
        }
        if (errorMessage != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(errorMessage),
                this.width / 2, cancelBtnY + 20 + 10, 0xFFFF5555);
        }

        String hint = useCustom
            ? "当前：自定义 " + (addressField.getText().isEmpty() ? "(未填写)" : addressField.getText() + ":" + portField.getText())
            : "当前：默认 " + cfg.getDefaultAddr() + ":" + cfg.getDefaultPort();
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(hint), this.width / 2, hintY, 0x808080);

        addressField.render(context, mouseX, mouseY, delta);
        portField.render(context, mouseX, mouseY, delta);
        tokenField.render(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }
}
