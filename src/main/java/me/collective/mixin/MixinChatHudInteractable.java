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
 * Тот же кастомный шрифт для второго бэкенда чата (открытый чат).
 */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess")
public abstract class MixinChatHudInteractable {

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