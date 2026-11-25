package me.collective.mixin;

import me.collective.client.render.ParticleInitialAlpha;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Particle.class)
public class SingleQuadParticleMixin implements ParticleInitialAlpha {
   @Shadow
   protected float alpha;
   @Unique
   private float initialAlpha;

   @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDD)V", at = @At("TAIL"))
   private void captureInitialAlphaFromPosition(
      ClientLevel level, double x, double y, double z, CallbackInfo callback
   ) {
      this.initialAlpha = this.alpha;
   }

   @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)V", at = @At("TAIL"))
   private void captureInitialAlphaFromVelocity(
      ClientLevel level, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfo callback
   ) {
      this.initialAlpha = this.alpha;
   }

   @ModifyVariable(method = "setAlpha", at = @At("HEAD"), ordinal = 0, argsOnly = true)
   private float scaleByInitialAlpha(float requestedAlpha) {
      return requestedAlpha * this.initialAlpha;
   }

   @Override
   public void collective$setInitialAlpha(float newAlpha) {
      this.initialAlpha = newAlpha;
      this.alpha = newAlpha;
   }

   @Override
   public float collective$getInitialAlpha() {
      return this.initialAlpha;
   }
}
