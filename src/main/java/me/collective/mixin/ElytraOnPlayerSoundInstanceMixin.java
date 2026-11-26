package me.collective.mixin;

import me.collective.client.ClientContext;
import me.collective.client.modules.movement.ElytraFlyModule;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.ElytraOnPlayerSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraOnPlayerSoundInstance.class)
public abstract class ElytraOnPlayerSoundInstanceMixin extends AbstractTickableSoundInstance {
   private static ElytraFlyModule elytrafly;
   private static ElytraFlyModule elytrafly() {
      if (elytrafly == null) {
         elytrafly = ClientContext.getModule(ElytraFlyModule.class);
      }
      return elytrafly;
   }

   protected ElytraOnPlayerSoundInstanceMixin(SoundEvent sound, SoundSource source, RandomSource random) {
      super(sound, source, random);
   }

   @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
   private void muteElytraFlightSound(CallbackInfo callback) {
      if (elytrafly().isToggled() && elytrafly().muteElytraSetting.getValue()) {
         this.stop();
         callback.cancel();
      }
   }
}
