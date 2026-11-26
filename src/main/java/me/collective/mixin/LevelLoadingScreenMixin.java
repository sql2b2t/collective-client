package me.collective.mixin;

import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelLoadingScreen.class)
public class LevelLoadingScreenMixin {
   @Shadow
   private LevelLoadTracker loadTracker;

   @Inject(method = "shouldCloseOnEsc", at = @At("HEAD"), cancellable = true)
   private void collective$allowClosingWithEscape(CallbackInfoReturnable<Boolean> callback) {
      callback.setReturnValue(this.loadTracker != null && this.loadTracker.isLevelReady());
   }
}