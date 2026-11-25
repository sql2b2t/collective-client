package me.collective.mixin;

import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelLoadingScreen.class)
public class LevelLoadingScreenMixin {
   @Shadow
   @Final
   private StoringChunkProgressListener progressListener;

   @Inject(method = "shouldCloseOnEsc", at = @At("HEAD"), cancellable = true)
   private void collective$allowClosingWithEscape(CallbackInfoReturnable<Boolean> callback) {
      callback.setReturnValue(this.progressListener != null && this.progressListener.getProgress() >= 100);
   }
}