package me.collective.mixin;

import me.collective.client.ClientContext;
import me.collective.client.modules.render.NoRenderModule;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldBorderRenderer.class)
public class WorldBorderRendererMixin {
   private static NoRenderModule noRenderModule;
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }

   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   private void suppressWorldBorder(
      WorldBorder borderState, Vec3 cameraPosition, double ignoredRenderDouble1, double ignoredRenderDouble2, CallbackInfo callback
   ) {
      if (noRenderModule().isToggled()
         && noRenderModule().worldBorderSetting.getValue()) {
         callback.cancel();
      }
   }
}
