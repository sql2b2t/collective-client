package me.collective.mixin;

import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.modules.render.NoRenderModule;
import me.collective.client.events.ParticleEngineAddEvent;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
   private static NoRenderModule noRenderModule;
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }

   @Shadow
   @Nullable
   protected abstract Particle makeParticle(ParticleOptions options, double x, double y, double z, double velocityX, double velocityY, double velocityZ);

   @Inject(method = "add", at = @At("HEAD"), cancellable = true)
   private void postParticleAddEvent(Particle particle, CallbackInfo callback) {
      ParticleEngineAddEvent event = new ParticleEngineAddEvent(particle);
      ClientAccess.eventBus
         .post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
   private void suppressSelectedParticle(
      ParticleOptions options, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> callback
   ) {
      if (noRenderModule().shouldHideParticle(
         options
      )) {
         callback.setReturnValue(this.makeParticle(options, x, y, z, velocityX, velocityY, velocityZ));
         callback.cancel();
      }
   }
}
