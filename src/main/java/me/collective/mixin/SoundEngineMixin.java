package me.collective.mixin;

import me.collective.client.ClientAccess;
import me.collective.client.events.SoundEnginePlayEvent;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngine.PlayResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

   @Inject(method = "play", at = @At("HEAD"), cancellable = true)
   private void postSoundPlayEvent(
      SoundInstance sound, CallbackInfoReturnable<PlayResult> callback
   ) {
      SoundEnginePlayEvent event = new SoundEnginePlayEvent(sound);
      ClientAccess.eventBus
         .post(event);
      if (event.isCancelled()) {
         callback.setReturnValue(PlayResult.NOT_STARTED);
      }
   }
}
