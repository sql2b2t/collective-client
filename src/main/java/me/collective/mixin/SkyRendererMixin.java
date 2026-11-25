package me.collective.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import me.collective.client.ClientContext;
import me.collective.client.render.RenderTargetUtils;
import me.collective.client.render.MaskShaderRenderer;
import me.collective.client.render.RenderUtils;
import me.collective.client.modules.render.NoRenderModule;
import me.collective.client.modules.render.SkyboxModule;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
   private static SkyboxModule skyboxModule;
   private static NoRenderModule noRenderModule;
   private static SkyboxModule skyboxModule() {
      if (skyboxModule == null) {
         skyboxModule = ClientContext.getModule(SkyboxModule.class);
      }
      return skyboxModule;
   }
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }
   @Unique
   private static boolean hook = false;

   @ModifyArgs(
      method = "renderSkyDisc",
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V",
         ordinal = 0
      )
   )
   private void overrideSkyDiscColor(Args arguments) {
      if (skyboxModule().isToggled()
         && skyboxModule().isCurrentDimensionEnabled()) {
         Color skyColor = skyboxModule().skySetting.getValue();
         arguments.set(0, skyColor.getRed() / 255.0F);
         arguments.set(1, skyColor.getGreen() / 255.0F);
         arguments.set(2, skyColor.getBlue() / 255.0F);
      }
   }

   @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
   private void hideDarkDiscInFlatSky(PoseStack poses, CallbackInfo callback) {
      if (skyboxModule().isToggled()
         && skyboxModule().isCurrentDimensionEnabled()
         && skyboxModule().typeSetting.getValue() == SkyboxModule.SkyType.FLAT) {
         callback.cancel();
      }
   }

   @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
   private void hideSun(float brightness, MultiBufferSource bufferSource, PoseStack poses, CallbackInfo callback) {
      if (noRenderModule().shouldHideCelestialBodies()) {
         callback.cancel();
      }
   }

   @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
   private void hideMoon(int moonPhase, float brightness, MultiBufferSource bufferSource, PoseStack poses, CallbackInfo callback) {
      if (noRenderModule().shouldHideCelestialBodies()) {
         callback.cancel();
      }
   }

   @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
   private void hideStars(FogParameters fogParameters, float brightness, PoseStack poses, CallbackInfo callback) {
      if (noRenderModule().shouldHideStars()) {
         callback.cancel();
      }
   }

   @Inject(method = "renderStars", at = @At("TAIL"))
   private void renderFancyStarBloom(
      FogParameters fogParameters, float brightness, PoseStack poses, CallbackInfo callback
   ) {
      if (skyboxModule().isToggled()
         && skyboxModule().fancyStarsSetting.getValue()) {
         if (!hook) {
            this.renderStarsToBloomMask(fogParameters, brightness, poses);
         }
      }
   }

   @ModifyArgs(
      method = "renderStars",
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V",
         ordinal = 0
      )
   )
   public void overrideStarTint(Args arguments) {
      if (skyboxModule().isToggled()
         && skyboxModule().fancyStarsSetting.getValue()) {
         Color starColor = skyboxModule().starsColorSetting.getValue();
         float originalAlpha = arguments.get(3);
         arguments.set(0, starColor.getRed() / 255.0F * originalAlpha);
         arguments.set(1, starColor.getGreen() / 255.0F * originalAlpha);
         arguments.set(2, starColor.getBlue() / 255.0F * originalAlpha);
      }
   }

   @Unique
   private void renderStarsToBloomMask(FogParameters fogParameters, float brightness, PoseStack poses) {
      RenderTarget previousTarget = Minecraft.getInstance().getMainRenderTarget();
      RenderUtils.clearColor(
         MaskShaderRenderer.MASK_TARGET,
         skyboxModule().starsColorSetting.getValue()
      );
      RenderUtils.clearDepth(
         MaskShaderRenderer.MASK_TARGET
      );
      RenderTargetUtils.setMainRenderTarget(
         MaskShaderRenderer.MASK_TARGET
      );
      hook = true;
      try {
         this.renderStars(fogParameters, brightness, poses);
      } finally {
         hook = false;
         RenderTargetUtils.setMainRenderTarget(previousTarget);
      }
      MaskShaderRenderer.setParameters(4.0F, 1.5F, 1.0F, 2.0F);
      MaskShaderRenderer.renderEffect("bloom_mask");
   }

   @Shadow
   private void renderStars(FogParameters fogParameters, float brightness, PoseStack poses) {
   }
}
