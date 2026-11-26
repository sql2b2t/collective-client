package me.collective.mixin;

import me.collective.client.modules.render.ChamsModule;
import me.collective.client.ClientContext;
import me.collective.client.render.RenderUtils;
import me.collective.client.modules.render.GlintModule;
import me.collective.client.managers.ModuleRegistry;
import me.collective.client.render.ClientRenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(RenderType.class)
public class RenderTypeColorMixin {
   private static GlintModule glintModule;
   private static ChamsModule chamsGlintModule;
   private static GlintModule glintModule() {
      if (glintModule == null) {
         glintModule = ClientContext.getModule(GlintModule.class);
      }
      return glintModule;
   }
   private static ChamsModule chamsGlintModule() {
      if (chamsGlintModule == null) {
         chamsGlintModule = ClientContext.getModule(ChamsModule.class);
      }
      return chamsGlintModule;
   }
   @Shadow
   @Final
   public RenderSetup state;

   @ModifyArg(
      method = "draw",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
      ),
      index = 1
   )
   private Vector4fc applyRenderColorModifiers(Vector4fc originalColor) {
      Vector4f mutableColor = (Vector4f)originalColor;
      if (ClientRenderPipelines.isGlint(this.state.pipeline)
         && glintModule().isToggled()) {
         mutableColor.mul(glintModule().getGlintColor());
      }

      if (ClientRenderPipelines.isGlint(this.state.pipeline)
         && ChamsModule.renderingGlintBatch) {
         mutableColor.set(
            chamsGlintModule().getGlintColor(),
            1.0F
         );
      }

      if (RenderUtils.getRenderColor()[3]
         != 255) {
         mutableColor.mul(
            1.0F,
            1.0F,
            1.0F,
            Mth.clamp(
               RenderUtils.getRenderColor()[3]
                  / 255.0F,
               0.0F,
               1.0F
            )
         );
      }

      return mutableColor;
   }

   @Unique
   private static void collective$bindRenderTypeColorModules(ModuleRegistry moduleRegistry) {
      glintModule = (GlintModule)moduleRegistry.getModule(
         GlintModule.class
      );
      chamsGlintModule = (ChamsModule)moduleRegistry.getModule(
         ChamsModule.class
      );
   }
}
