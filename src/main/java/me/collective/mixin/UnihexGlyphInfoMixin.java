package me.collective.mixin;

import me.collective.client.render.GlyphShadowOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// UnihexProvider$Glyph переопределяет getShadowOffset (0.5F) и минует
// default-метод интерфейса GlyphInfo, поэтому GlyphInfoMixin на него не действует.
@Mixin(targets = "net/minecraft/client/gui/font/providers/UnihexProvider$Glyph")
public class UnihexGlyphInfoMixin {
   @Inject(method = "getShadowOffset", at = @At("HEAD"), cancellable = true)
   private void modifyTextShadowOffset(CallbackInfoReturnable<Float> callback) {
      if (GlyphShadowOverride.isOverridden()) {
         callback.setReturnValue(0.6F);
      }
   }
}