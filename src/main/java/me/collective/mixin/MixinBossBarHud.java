package me.collective.mixin;

import me.collective.font.FontScopes;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Шрифт Fonts для имён боссов (скоуп «Боссбар»). */
@Mixin(BossBarHud.class)
public abstract class MixinBossBarHud {

    @Redirect(method = "render",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow"
                + "(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"))
    private void drawCustomBossBarText(DrawContext context, TextRenderer renderer,
                                       Text text, int x, int y, int color) {
        if (FontScopes.bossBar()) {
            FontScopes.draw(context, text, x, y, color, true);
        } else {
            context.drawTextWithShadow(renderer, text, x, y, color);
        }
    }
}
