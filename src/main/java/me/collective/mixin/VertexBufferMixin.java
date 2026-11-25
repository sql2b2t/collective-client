package me.collective.mixin;

import com.mojang.blaze3d.vertex.VertexBuffer;
import me.collective.client.managers.RenderStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexBuffer.class)
public class VertexBufferMixin {
   @Inject(method = "draw", at = @At("HEAD"))
   private void applyCustomRenderStatesBeforeDraw(CallbackInfo callback) {
      RenderStateManager.applyRegistered(true);
   }

   @Inject(method = "draw", at = @At("RETURN"))
   private void restoreCustomRenderStatesAfterDraw(CallbackInfo callback) {
      RenderStateManager.applyRegistered(false);
   }
}
