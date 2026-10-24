package me.collective.mixin;

import me.collective.font.FontScopes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Шрифт Fonts для имён боссов (скоуп «Боссбар»). */
@Mixin(BossBarHud.class)
public abstract class MixinBossBarHud {

    @Redirect(method = "render",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawTextWithShadow"
                + "(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Component;III)V"))
    private void drawCustomBossBarText(GuiGraphics context, TextRenderer renderer,
                                       Component text, int x, int y, int color) {
        if (FontScopes.bossBar()) {
            FontScopes.draw(context, text, x, y, color, true);
        } else {
            context.drawTextWithShadow(renderer, text, x, y, color);
        }
    }
}