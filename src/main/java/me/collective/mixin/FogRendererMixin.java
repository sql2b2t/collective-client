package me.collective.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBuffer.MappedView;
import com.mojang.blaze3d.systems.RenderSystem;
import me.collective.client.ClientContext;
import me.collective.client.modules.render.NoRenderModule;
import me.collective.client.modules.render.SkyboxModule;
import java.awt.Color;
import java.nio.ByteBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
   @Unique private float capturedEnvironmentalStart;
   @Unique private float capturedEnvironmentalEnd;
   @Unique private float capturedRenderDistanceStart;
   @Unique private float capturedRenderDistanceEnd;
   @Unique private float capturedSkyEnd;
   @Unique private float capturedCloudsEnd;
   @Unique private boolean hasCapturedFog;

   @Inject(method = "updateBuffer", at = @At("HEAD"))
   private void captureFogDistances(
      ByteBuffer buffer, int bufferOffset, Vector4f fogColor, float environmentalStart, float environmentalEnd, float renderDistanceStart, float renderDistanceEnd, float skyEnd, float cloudsEnd, CallbackInfo callback
   ) {
      this.capturedEnvironmentalStart = environmentalStart;
      this.capturedEnvironmentalEnd = environmentalEnd;
      this.capturedRenderDistanceStart = renderDistanceStart;
      this.capturedRenderDistanceEnd = renderDistanceEnd;
      this.capturedSkyEnd = skyEnd;
      this.capturedCloudsEnd = cloudsEnd;
      this.hasCapturedFog = true;
   }

   @Inject(
      method = "setupFog",
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/blaze3d/systems/GpuDevice;",
         shift = Shift.BEFORE,
         remap = false
      )
   )
   private void applyNoRenderFogSettings(
      Camera camera, int renderDistance, DeltaTracker deltaTracker, float ignoredSetupFloat, ClientLevel level, CallbackInfoReturnable<Vector4f> callback, @Local Vector4f fogColor, @Local FogData fogData
   ) {
      NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
      if (noRenderModule != null
         && noRenderModule.isToggled()
         && noRenderModule.fogSetting.getValue()) {
         noRenderModule.applyFogSettings(
            fogData, renderDistance
         );
      }

      SkyboxModule skyboxModule = ClientContext.getModule(SkyboxModule.class);
      if (skyboxModule != null
         && skyboxModule.isToggled()
         && skyboxModule.isCurrentDimensionEnabled()) {
         Color configuredColor = skyboxModule.fogSetting.getValue();
         float red = configuredColor.getRed() / 255.0F;
         float green = configuredColor.getGreen() / 255.0F;
         float blue = configuredColor.getBlue() / 255.0F;
         float alpha = configuredColor.getAlpha() / 255.0F;
         fogColor.set(red, green, blue, alpha);
      }
   }

   @Inject(method = "setupFog", at = @At("TAIL"))
   private void updateSplitSkyFogBuffer(
      Camera camera, int renderDistance, DeltaTracker deltaTracker, float ignoredSetupFloat, ClientLevel level, CallbackInfoReturnable<Vector4f> callback
   ) {
      SkyboxModule skyboxModule = ClientContext.getModule(SkyboxModule.class);
      if (skyboxModule != null
         && skyboxModule.isToggled()
         && skyboxModule.splitFogSetting.getValue()
         && this.hasCapturedFog) {
         Color backgroundColor = skyboxModule.backgroundSetting.getValue();
         Vector4f skyColor = new Vector4f(backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue(), backgroundColor.getAlpha()).div(255.0F);

         try (MappedView mappedBuffer = RenderSystem.getDevice()
               .createCommandEncoder()
               .mapBuffer(
                  skyboxModule.getSkyFogBuffer()
                     .currentBuffer(),
                  false,
                  true
               )) {
            this.updateBuffer(
               mappedBuffer.data(),
               0,
               skyColor,
               this.capturedEnvironmentalStart,
               this.capturedEnvironmentalEnd,
               this.capturedRenderDistanceStart,
               this.capturedRenderDistanceEnd,
               this.capturedSkyEnd,
               this.capturedCloudsEnd
            );
         }
      }
   }

   @Shadow
   protected abstract void updateBuffer(ByteBuffer buffer, int bufferOffset, Vector4f fogColor, float environmentalStart, float environmentalEnd, float renderDistanceStart, float renderDistanceEnd, float skyEnd, float cloudsEnd);
}
