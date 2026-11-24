package me.collective.mixin;

import me.collective.client.managers.ModuleRegistry;
import net.minecraft.world.level.block.WebBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WebBlock.class)
public interface WebBlockModuleBindings {
   @Invoker(value = "collective$bindWebBlockModules", remap = false)
   static void bind(ModuleRegistry registry) {
      throw new AssertionError("Mixin invoker was not applied");
   }
}
