package me.collective.mixin;

import me.collective.font.FontScopes;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Шрифт Fonts для заголовков контейнеров (скоуп «Инвентарь»). */
@Mixin(HandledScreen.class)
public abstract class MixinHandledScreen {

    @Redirect(method = "drawForeground",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawText"
                + "(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V"))
    private void drawCustomScreenTitle(DrawContext context, TextRenderer renderer,
                                       Text text, int x, int y, int color, boolean shadow) {
        if (FontScopes.inventory()) {
            FontScopes.draw(context, text, x, y, color, shadow);
        } else {
            context.drawText(renderer, text, x, y, color, shadow);
        }
    }
}
