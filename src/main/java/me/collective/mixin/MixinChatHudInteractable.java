package me.collective.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Тот же кастомный шрифт для второго бэкенда чата.
 *
 * <p>Когда чат открыт (фокус/наведение для кликабельных ссылок), строки
 * рисует {@code ChatHud$Interactable}, а не {@code ChatHud$Hud} — без этого
 * хука шрифт слетал на ванильный при открытии чата.
 */
@Mixin(targets = "net.minecraft.client.gui.hud.ChatHud$Interactable")
public abstract class MixinChatHudInteractable {

    @Shadow
    @Final
    private GuiGraphics context;

    @Inject(
        method = "text(IFLnet/minecraft/text/OrderedText;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void drawCustomChatFont(int y, float opacity, OrderedText text, CallbackInfoReturnable<Boolean> cir) {
        if (me.collective.font.ChatFontUtil.drawCustomChat(context, y, opacity, text)) cir.setReturnValue(true);
    }
}