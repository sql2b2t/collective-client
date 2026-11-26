package me.collective.mixin;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.particle.FireworkParticles.SparkParticle;
import net.minecraft.client.particle.FireworkParticles.Starter;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Starter.class)
public class FireworkParticlesStarterMixin {
   @Final
   @Shadow
   private ParticleEngine engine;

   @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
   private void cancelUnavailableFireworkParticle(
      double x,
      double y,
      double z,
      double velocityX,
      double velocityY,
      double velocityZ,
      IntList colors,
      IntList fadeColors,
      boolean trail,
      boolean twinkle,
      CallbackInfo callback
   ) {
      SparkParticle spark = (SparkParticle)this.engine.createParticle(ParticleTypes.FIREWORK, x, y, z, velocityX, velocityY, velocityZ);
      if (spark == null) {
         callback.cancel();
      }
   }
}
