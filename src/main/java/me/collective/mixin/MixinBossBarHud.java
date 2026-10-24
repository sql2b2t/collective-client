package me.collective.mixin;

import me.collective.font.FontScopes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BossHealthOverlay.class)
public abstract class MixinBossBarHud {

    @Redirect(method = "extractRenderState",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void drawCustomBossBarText(GuiGraphicsExtractor context, Font renderer,
                                       Component text, int x, int y, int color) {
        if (FontScopes.bossBar()) {
            FontScopes.draw(context, text, x, y, color, true);
        } else {
            context.text(renderer, text, x, y, color);
        }
    }
}