package com.mcfrp.mixin;

import com.mcfrp.McFrpClient;
import com.mcfrp.util.InviteCodeUtil;
import com.mcfrp.visitor.VisitorManager;
import net.minecraft.client.gui.screen.AddServerScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Mixin(AddServerScreen.class)
public abstract class AddServerScreenMixin extends Screen {

    protected AddServerScreenMixin(Text title) { super(title); }

    private TextFieldWidget mcfrp$addressField;
    private ButtonWidget mcfrp$saveButton;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        try {
            List<Object> allChildren = collectAllChildren(this.children());
            for (var child : allChildren) {
                if (child instanceof TextFieldWidget tf) {
                    mcfrp$addressField = tf;
                } else if (child instanceof ButtonWidget bw) {
                    String msg = bw.getMessage().getString();
                    if (msg != null && (msg.contains("完成") || msg.contains("Done") || msg.contains("保存") || msg.contains("Save"))) {
                        mcfrp$saveButton = bw;
                    }
                }
            }

            if (mcfrp$addressField != null && mcfrp$saveButton != null) {
                Field f = findFieldByType(ButtonWidget.class, ButtonWidget.PressAction.class);
                if (f == null) return;
                f.setAccessible(true);
                Object original = f.get(mcfrp$saveButton);
                final TextFieldWidget finalAddress = mcfrp$addressField;
                ButtonWidget.PressAction wrapped = btn -> {
                    String text = finalAddress.getText() == null ? "" : finalAddress.getText().trim();
                    if (text.startsWith("MCFRP://")) {
                        if (InviteCodeUtil.isInviteCode(text)) {
                            VisitorManager.joinWithSave(text, this);
                            return;
                        }
                        finalAddress.setText("✗ 邀请码无效，请检查后重试");
                        finalAddress.setCursorToEnd();
                        finalAddress.setSelectionStart(0);
                        finalAddress.setEditableColor(0xFFFF5555);
                        return;
                    }
                    if (original instanceof ButtonWidget.PressAction pa) pa.onPress(btn);
                };
                f.set(mcfrp$saveButton, wrapped);
            }
        } catch (Throwable t) {
            McFrpClient.LOGGER.warn("[MCFRP] Mixin 初始化失败 (AddServerScreen)", t);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (mcfrp$addressField != null && mcfrp$saveButton != null) {
            String text = mcfrp$addressField.getText();
            if (text != null && text.trim().startsWith("MCFRP://")) {
                mcfrp$saveButton.active = true;
            }
        }
    }

    private static Field findFieldByType(Class<?> cls, Class<?> type) {
        for (Field f : cls.getDeclaredFields()) {
            if (type.isAssignableFrom(f.getType())) return f;
        }
        Class<?> sup = cls.getSuperclass();
        if (sup != null) return findFieldByType(sup, type);
        return null;
    }

    private static List<Object> collectAllChildren(List<? extends net.minecraft.client.gui.Element> elements) {
        List<Object> out = new ArrayList<>();
        if (elements == null) return out;
        for (var e : elements) {
            if (e != null) {
                out.add(e);
                if (e instanceof net.minecraft.client.gui.ParentElement pe) {
                    try {
                        out.addAll(collectAllChildren(pe.children()));
                    } catch (Throwable ignore) {}
                }
            }
        }
        return out;
    }
}
