package me.collective.mixin;

import me.collective.font.ChatFontUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Замена шрифта чата на шрифт модуля Fonts (закрытый чат, бэкенд Hud).
 */
@Mixin(targets = "net.minecraft.client.gui.hud.ChatHud$Hud")
public abstract class MixinChatHud {

    @Shadow
    @Final
    private GuiGraphicsExtractor context;

    @Inject(
        method = "text(IFLnet/minecraft/text/FormattedCharSequence;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void drawCustomChatFont(int y, float opacity, FormattedCharSequence text, CallbackInfoReturnable<Boolean> cir) {
        if (ChatFontUtil.drawCustomChat(context, y, opacity, text)) cir.setReturnValue(true);
    }
}