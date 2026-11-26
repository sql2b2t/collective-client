package me.collective.mixin;

import me.collective.client.managers.ModuleRegistry;
import net.minecraft.client.resources.model.sprite.SpriteId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SpriteId.class)
public interface MaterialModuleBindings {
   @Invoker(value = "collective$bindMaterialModules", remap = false)
   static void bind(ModuleRegistry registry) {
      throw new AssertionError("Mixin invoker was not applied");
   }
}
