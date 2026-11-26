package me.collective.mixin;

import me.collective.client.managers.ModuleRegistry;
import net.minecraft.world.level.block.LiquidBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LiquidBlock.class)
public interface LiquidBlockModuleBindings {
   @Invoker(value = "collective$bindLiquidBlockModules", remap = false)
   static void bind(ModuleRegistry registry) {
      throw new AssertionError("Mixin invoker was not applied");
   }
}
