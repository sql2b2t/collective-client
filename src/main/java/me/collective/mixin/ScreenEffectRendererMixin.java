package me.collective.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.collective.client.ClientContext;
import me.collective.client.modules.player.FreecamModule;
import me.collective.client.modules.render.NoRenderModule;
import net.minecraft.client.Minecraft;
import me.collective.client.render.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
   private static NoRenderModule noRenderModule;
   private static FreecamModule freecamModule;
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }
   private static FreecamModule freecamModule() {
      if (freecamModule == null) {
         freecamModule = ClientContext.getModule(FreecamModule.class);
      }
      return freecamModule;
   }

   @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
   private static void suppressFireOverlay(
      PoseStack poses, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo callback
   ) {
      if (noRenderModule().isToggled()
         && noRenderModule().fireSetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
   private static void suppressWaterOverlay(
      Minecraft client, PoseStack poses, MultiBufferSource buffers, CallbackInfo callback
   ) {
      if (noRenderModule().isToggled()
         && noRenderModule().blindnessSetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
   private static void suppressTextureOverlay(
      TextureAtlasSprite sprite, PoseStack poses, MultiBufferSource buffers, CallbackInfo callback
   ) {
      if (noRenderModule().isToggled()
            && noRenderModule().blindnessSetting.getValue()
         || freecamModule().isToggled()) {
         callback.cancel();
      }
   }
}
