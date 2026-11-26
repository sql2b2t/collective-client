package me.collective.mixin;

import me.collective.client.ClientContext;
import me.collective.client.render.RenderUtils;
import me.collective.client.managers.ModuleRegistry;
import me.collective.client.modules.render.NoRenderModule;
import java.util.function.Function;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpriteId.class)
public class MaterialMixin {
   @Shadow
   @Final
   private Identifier atlasLocation;
   private static NoRenderModule noRenderModule;
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }

   @Inject(method = "renderType", at = @At("HEAD"), cancellable = true)
   private void applyTranslucentHandMaterial(Function renderTypeFactory, CallbackInfoReturnable<RenderType> callback) {
      if (noRenderModule().getHandOpacity()
            != 1.0F
         && RenderUtils.getRenderColor()[3]
            != 255) {
         callback.setReturnValue(RenderTypes.entityTranslucent(this.atlasLocation));
         callback.cancel();
      }
   }

   @Unique
   private static void collective$bindMaterialModules(ModuleRegistry moduleRegistry) {
      noRenderModule = (NoRenderModule)moduleRegistry.getModule(
         NoRenderModule.class
      );
   }
}
