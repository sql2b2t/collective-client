package me.collective.mixin;

import me.collective.client.managers.ModuleRegistry;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TextureTransform.class)
public interface TextureTransformModuleBindings {
   @Invoker(value = "collective$bindTextureTransformModules", remap = false)
   static void bind(ModuleRegistry registry) {
      throw new AssertionError("Mixin invoker was not applied");
   }
}
