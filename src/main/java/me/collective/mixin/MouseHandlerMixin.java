package me.collective.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.collective.client.ClientAccess;
import me.collective.client.events.InputPressEvent;
import me.collective.client.events.MouseHandlerOnScrollEvent;
import me.collective.client.events.MouseHandlerTurnPlayerEvent;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

   @Inject(method = "onButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V", shift = Shift.BEFORE), cancellable = true)
   private void postMouseButtonPressEvent(
      long windowHandle, MouseButtonInfo buttonInfo, int action, CallbackInfo callback
   ) {
      if (action != 1) {
         return;
      }
      InputPressEvent event = new InputPressEvent(buttonInfo.button(), true);
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @WrapWithCondition(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
   private boolean allowPlayerTurn(
      LocalPlayer player, double yawDelta, double pitchDelta
   ) {
      MouseHandlerTurnPlayerEvent event = new MouseHandlerTurnPlayerEvent(yawDelta, pitchDelta);
      ClientAccess.eventBus.post(event);
      return !event.isCancelled();
   }

   @Inject(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z", shift = Shift.BEFORE), cancellable = true)
   private void postMouseScrollEvent(
      long windowHandle, double horizontalScroll, double verticalScroll, CallbackInfo callback
   ) {
      MouseHandlerOnScrollEvent event = new MouseHandlerOnScrollEvent(horizontalScroll, verticalScroll);
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }
}
