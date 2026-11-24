package me.collective.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.collective.client.render.EntityChamsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.GuardianRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuardianRenderer.class)
public class GuardianRendererMixin {
   @Inject(method = "renderBeam", at = @At("HEAD"), cancellable = true)
   private static void suppressGuardianBeamDuringChams(
      PoseStack poses, SubmitNodeCollector collector, Vec3 beamVector, float ignoredRenderFloat1, float ignoredRenderFloat2, float ignoredRenderFloat3, CallbackInfo callback
   ) {
      if (EntityChamsRenderer.isRenderingEntity()) {
         callback.cancel();
      }
   }
}
