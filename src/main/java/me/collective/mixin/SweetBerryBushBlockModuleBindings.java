package me.collective.mixin;

import me.collective.client.managers.ModuleRegistry;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SweetBerryBushBlock.class)
public interface SweetBerryBushBlockModuleBindings {
   @Invoker(value = "collective$bindSweetBerryBushBlockModules", remap = false)
   static void bind(ModuleRegistry registry) {
      throw new AssertionError("Mixin invoker was not applied");
   }
}
