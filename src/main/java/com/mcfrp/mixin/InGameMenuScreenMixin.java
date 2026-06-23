package com.mcfrp.mixin;

import com.mcfrp.McFrpClient;
import com.mcfrp.ui.FrpcControlScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;

@Mixin(GameMenuScreen.class)
public abstract class InGameMenuScreenMixin extends Screen {

    protected InGameMenuScreenMixin(Text title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        try {
            // 先移除旧版的"联机管理"按钮（如果 init 不清空 children 时避免重复）
            Iterator<? extends net.minecraft.client.gui.Element> it = this.children().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (e instanceof ButtonWidget bw && "联机管理".equals(bw.getMessage().getString())) {
                    it.remove();
                }
            }

            // 找到所有 widget 中最底部那个
            int maxBottom = 0;
            int refH = 20;
            int refX = 0;
            int refW = 200;
            for (var child : this.children()) {
                if (child instanceof ClickableWidget w) {
                    int bottom = w.getY() + w.getHeight();
                    if (bottom > maxBottom) {
                        maxBottom = bottom;
                        refH = w.getHeight();
                        refX = w.getX();
                        refW = w.getWidth();
                    }
                }
            }
            if (maxBottom == 0) return;

            int ourY = maxBottom + 4;

            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("联机管理"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new FrpcControlScreen(this));
                    }
                }
            ).dimensions(refX, ourY, refW, refH).build());

        } catch (Throwable t) {
            McFrpClient.LOGGER.warn("[MCFRP] Mixin 初始化失败 (InGameMenu): 已回退到原版行为", t);
        }
    }
}
