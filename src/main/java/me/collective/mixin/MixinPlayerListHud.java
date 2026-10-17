package me.collective.mixin;

import me.collective.font.FontScopes;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Шрифт Fonts для таб-листа (скоуп «Таб»): имена, шапка, подвал, счёт. */
@Mixin(PlayerListHud.class)
public abstract class MixinPlayerListHud {

    @Redirect(method = {"render", "renderScoreboardObjective", "renderHearts"},
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow"
                + "(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"))
    private void drawCustomTabText(DrawContext context, TextRenderer renderer,
                                   Text text, int x, int y, int color) {
        if (FontScopes.tab()) {
            FontScopes.draw(context, text, x, y, color, true);
        } else {
            context.drawTextWithShadow(renderer, text, x, y, color);
        }
    }
}
