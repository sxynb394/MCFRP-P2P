package com.mcfrp.mixin;

import com.mcfrp.ui.InviteCodeScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {

    protected MultiplayerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        MultiplayerScreen self = (MultiplayerScreen) (Object) this;

        // 获取 Screen 的所有按钮
        List<ClickableWidget> allButtons = getAllClickableWidgets();
        if (allButtons.isEmpty()) {
            addInviteButtonDefault(self);
            return;
        }

        // 找出底部区域的按钮（y >= 屏幕高度 - 60）
        int bottomThreshold = this.height - 60;
        List<ClickableWidget> bottomButtons = new ArrayList<>();
        for (ClickableWidget widget : allButtons) {
            if (widget instanceof ButtonWidget) {
                int by = widget.getY();
                if (by >= bottomThreshold && by <= this.height - 20) {
                    bottomButtons.add(widget);
                }
            }
        }

        if (bottomButtons.isEmpty()) {
            // 没找到底部按钮？尝试获取最底部的几个按钮
            allButtons.sort(Comparator.comparingInt(ClickableWidget::getY).reversed());
            for (ClickableWidget w : allButtons) {
                if (w instanceof ButtonWidget && bottomButtons.size() < 4) {
                    bottomButtons.add(w);
                }
            }
        }

        if (!bottomButtons.isEmpty()) {
            bottomButtons.sort(Comparator.comparingInt(ClickableWidget::getX));

            int gap = 4;
            int btnHeight = 20;
            int inviteBtnWidth = 120;
            int margin = 10;

            // 找出最底部按钮的 Y 值（第二排按钮）
            int bottomY = bottomButtons.stream()
                .mapToInt(ClickableWidget::getY)
                .max()
                .orElse(this.height - 28);

            // 计算需要向左偏移的距离（为邀请码按钮腾出空间）
            int shiftLeft = inviteBtnWidth / 2 + gap;

            // 整体向左移动所有底部按钮，保持原有相对位置
            for (ClickableWidget button : bottomButtons) {
                int originalX = button.getX();
                button.setX(originalX - shiftLeft);
            }

            // 邀请码按钮放在最右边，与最底部按钮对齐
            int inviteX = this.width - margin - inviteBtnWidth;
            ButtonWidget inviteButton = ButtonWidget.builder(
                    Text.literal("通过邀请码加入"),
                    button -> MinecraftClient.getInstance().setScreen(new InviteCodeScreen(self))
                )
                .dimensions(inviteX, bottomY, inviteBtnWidth, btnHeight)
                .build();
            this.addDrawableChild(inviteButton);

        } else {
            addInviteButtonDefault(self);
        }
    }

    private void addInviteButtonDefault(MultiplayerScreen self) {
        ButtonWidget inviteButton = ButtonWidget.builder(
                Text.literal("通过邀请码加入"),
                button -> MinecraftClient.getInstance().setScreen(new InviteCodeScreen(self))
            )
            .dimensions(this.width / 2 - 100, this.height - 28, 200, 20)
            .build();
        this.addDrawableChild(inviteButton);
    }

    // 获取 Screen 的所有 ClickableWidget（包括按钮）
    private List<ClickableWidget> getAllClickableWidgets() {
        List<ClickableWidget> widgets = new ArrayList<>();

        // 通过 children() 获取所有子元素
        for (var child : this.children()) {
            if (child instanceof ClickableWidget widget) {
                widgets.add(widget);
            }
        }

        return widgets;
    }
}