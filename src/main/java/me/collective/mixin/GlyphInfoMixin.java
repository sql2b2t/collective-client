package me.collective.mixin;

import com.mojang.blaze3d.font.GlyphInfo;
import me.collective.client.render.GlyphShadowOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlyphInfo.class)
public interface GlyphInfoMixin {
   @Inject(method = "getShadowOffset", at = @At("HEAD"), cancellable = true)
   default void modifyTextShadowOffset(CallbackInfoReturnable<Float> callback) {
      if (GlyphShadowOverride.isOverridden()) {
         callback.setReturnValue(0.6F);
      }
   }
}
