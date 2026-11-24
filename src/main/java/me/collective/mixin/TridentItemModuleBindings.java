package me.collective.mixin;

import me.collective.client.managers.ModuleRegistry;
import net.minecraft.world.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TridentItem.class)
public interface TridentItemModuleBindings {
   @Invoker(value = "collective$bindTridentItemModules", remap = false)
   static void bind(ModuleRegistry registry) {
      throw new AssertionError("Mixin invoker was not applied");
   }
}
