package me.collective.mixin;

import me.collective.font.FontScopes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Шрифт Fonts для заголовков контейнеров (скоуп «Инвентарь»). */
@Mixin(AbstractContainerScreen.class)
public abstract class MixinHandledScreen {

    @Redirect(method = "drawForeground",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;drawText"
                + "(Lnet/minecraft/client/font/Font;Lnet/minecraft/text/Component;IIIZ)V"))
    private void drawCustomScreenTitle(GuiGraphicsExtractor context, Font renderer,
                                       Component text, int x, int y, int color, boolean shadow) {
        if (FontScopes.inventory()) {
            FontScopes.draw(context, text, x, y, color, shadow);
        } else {
            context.text(renderer, text, x, y, color, shadow);
        }
    }
}