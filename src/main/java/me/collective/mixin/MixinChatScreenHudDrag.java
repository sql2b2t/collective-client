package me.collective.mixin;

import me.collective.gui.hud.ChatHudDrag;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Перетаскивание HUD-модулей поверх открытого чата: перехватываем клик
 * мыши в ChatScreen — если под курсором HUD-модуль (вне зоны чата),
 * начинаем драг вместо передачи клика чату.
 */
@Mixin(ChatScreen.class)
public class MixinChatScreenHudDrag {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void collective$chatHudDragClick(net.minecraft.client.input.MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (ChatHudDrag.tryBeginDrag(click)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void collective$chatHudDragClose(CallbackInfo ci) {
        ChatHudDrag.endDrag();
    }
}