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
 * Замена шрифта чата на шрифт модуля Fonts (закрытый чат, бэкенд DrawingBackgroundGraphicsAccess).
 */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess")
public abstract class MixinChatHud {

    @Shadow
    @Final
    private GuiGraphicsExtractor graphics;

    @Inject(
        method = "handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void drawCustomChatFont(int y, float opacity, FormattedCharSequence text, CallbackInfoReturnable<Boolean> cir) {
        if (ChatFontUtil.drawCustomChat(graphics, y, opacity, text)) cir.setReturnValue(true);
    }
}