package me.collective.mixin;

import me.collective.client.ClientAccess;
import me.collective.client.events.ScreenChangeEvent;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
   @Unique
   private Screen previousScreen;

   @Shadow
   private Screen screen;

   @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
   private void postBeforeScreenChange(Screen newScreen, CallbackInfo callback) {
      this.previousScreen = this.screen;
      ScreenChangeEvent.Before event = new ScreenChangeEvent.Before(
         this.previousScreen, newScreen
      );
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;init(II)V", shift = Shift.AFTER), cancellable = true)
   private void postAfterScreenInit(Screen newScreen, CallbackInfo callback) {
      ScreenChangeEvent.AfterInit event = new ScreenChangeEvent.AfterInit(
         this.previousScreen, newScreen
      );
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }
}
