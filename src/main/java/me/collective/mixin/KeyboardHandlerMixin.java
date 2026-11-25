package me.collective.mixin;

import me.collective.client.modules.misc.BetterChatModule;
import me.collective.client.ClientAccess;
import me.collective.client.events.InputPressEvent;
import me.collective.client.events.KeyboardHandlerCharTypedEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
   @Shadow
   @Final
   private Minecraft minecraft;

   @Inject(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V", shift = Shift.BEFORE), cancellable = true)
   private void postKeyPressEvent(
      long windowHandle, int key, int scanCode, int action, int modifiers, CallbackInfo callback
   ) {
      if (key >= 0 && this.minecraft.screen == null && action == 1) {
         InputPressEvent event = new InputPressEvent(key, false);
         ClientAccess.eventBus
            .post(event);
         if (event.isCancelled()) {
            callback.cancel();
         }
      }
   }

   @Inject(method = "charTyped", at = @At("HEAD"))
   public void postCharTypedEvent(
      long windowHandle, int codePoint, int modifiers, CallbackInfo callback
   ) {
      if (this.minecraft.screen == null && windowHandle == this.minecraft.getWindow().getWindow()) {
         KeyboardHandlerCharTypedEvent event = new KeyboardHandlerCharTypedEvent(codePoint);
         ClientAccess.eventBus
            .post(event);
      }
   }

   @Inject(method = "handleDebugKeys", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;clearMessages(Z)V", shift = Shift.BEFORE))
   private void beginDebugChatClear(
      int key, CallbackInfoReturnable<Boolean> callback
   ) {
      BetterChatModule.clearingChatFromDebugKey = true;
   }

   @Inject(method = "handleDebugKeys", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;clearMessages(Z)V", shift = Shift.AFTER))
   private void endDebugChatClear(
      int key, CallbackInfoReturnable<Boolean> callback
   ) {
      BetterChatModule.clearingChatFromDebugKey = false;
   }
}
