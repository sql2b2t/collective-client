package me.collective.mixin;

import me.collective.client.modules.render.ChamsModule;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.events.GlintTextureTransformEvent;
import me.collective.client.managers.ModuleRegistry;
import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(RenderStateShard.class)
public class TextureTransformMixin {
   private static ChamsModule chamsModule;
   private static ChamsModule chamsModule() {
      if (chamsModule == null) {
         chamsModule = ClientContext.getModule(ChamsModule.class);
      }
      return chamsModule;
   }

   @ModifyArgs(method = "setupGlintTexturing", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;scale(F)Lorg/joml/Matrix4f;", remap = false))
   private static void modifyGlintScale(Args arguments) {
      GlintTextureTransformEvent event = GlintTextureTransformEvent.resetAndGet();
      ClientAccess.eventBus.post(event);
      arguments.set(0, arguments.<Float>get(0) * event.getScale());
   }

   @ModifyArgs(method = "setupGlintTexturing", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;translation(FFF)Lorg/joml/Matrix4f;", remap = false))
   private static void applyGlintProgressTranslation(Args arguments) {
      if (chamsModule().speedSetting
            .isAtMinimum()
         && ChamsModule.renderingGlintBatch) {
         float progress = (chamsModule().progressSetting.getValue())
               .intValue()
            / 100.0F;
         arguments.set(0, -progress);
         arguments.set(1, progress);
      }
   }

   @Unique
   private static void collective$bindTextureTransformModules(ModuleRegistry moduleRegistry) {
      chamsModule = (ChamsModule)moduleRegistry.getModule(
         ChamsModule.class
      );
   }
}
