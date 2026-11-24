package me.collective.mixin;

import me.collective.client.render.ParticleInitialAlpha;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
public class SingleQuadParticleMixin implements ParticleInitialAlpha {
   @Shadow
   protected float alpha;
   @Unique
   private float initialAlpha;

   @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDLnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V", at = @At("TAIL"))
   private void captureInitialAlphaFromPosition(
      ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, CallbackInfo callback
   ) {
      this.initialAlpha = this.alpha;
   }

   @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V", at = @At("TAIL"))
   private void captureInitialAlphaFromVelocity(
      ClientLevel level, double x, double y, double z, double velocityX, double velocityY, double velocityZ, TextureAtlasSprite sprite, CallbackInfo callback
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
