package me.collective.mixin;

import me.collective.client.ClientContext;
import me.collective.client.modules.render.NoRenderModule;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InterpolationHandler.class)
public abstract class InterpolationHandlerMixin {
   private static NoRenderModule noRenderModule;
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }
   @Shadow
   @Final
   private Entity entity;

   @Shadow
   public abstract void cancel();

   @Inject(method = "interpolateTo", at = @At("HEAD"), cancellable = true)
   private void suppressEntityInterpolation(
      Vec3 position, float yaw, float pitch, CallbackInfo callback
   ) {
      if (noRenderModule().isToggled()
         && noRenderModule().interpolationSetting.getValue()) {
         this.entity.snapTo(position, yaw, pitch);
         this.cancel();
         callback.cancel();
      }
   }
}
