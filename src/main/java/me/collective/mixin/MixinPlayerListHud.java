package me.collective.mixin;

import me.collective.font.FontScopes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Шрифт Fonts для таб-листа (скоуп «Таб»): имена, шапка, подвал, счёт. */
@Mixin(PlayerTabOverlay.class)
public abstract class MixinPlayerListHud {

    @Redirect(method = {"render", "renderScoreboardObjective", "renderHearts"},
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;drawTextWithShadow"
                + "(Lnet/minecraft/client/font/Font;Lnet/minecraft/text/Component;III)V"))
    private void drawCustomTabText(GuiGraphicsExtractor context, Font renderer,
                                   Component text, int x, int y, int color) {
        if (FontScopes.tab()) {
            FontScopes.draw(context, text, x, y, color, true);
        } else {
            context.text(renderer, text, x, y, color);
        }
    }
}