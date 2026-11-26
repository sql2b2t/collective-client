package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.collective.client.ClientContext;
import me.collective.client.managers.ModuleRegistry;
import me.collective.client.modules.movement.NoSlowModule;
import net.minecraft.world.level.block.HoneyBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HoneyBlock.class)
public class HoneyBlockMixin {
   private static NoSlowModule noSlowModule;
   private static NoSlowModule noSlowModule() {
      if (noSlowModule == null) {
         noSlowModule = ClientContext.getModule(NoSlowModule.class);
      }
      return noSlowModule;
   }

   @ModifyExpressionValue(
      method = "entityInside",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/HoneyBlock;isSlidingDown(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)Z")
   )
   private boolean suppressHoneySliding(boolean originalSliding) {
      return noSlowModule().isToggled()
            && noSlowModule().honeySetting.getValue()
         ? false
         : originalSliding;
   }

   @Unique
   private static void collective$bindHoneyBlockModules(ModuleRegistry moduleRegistry) {
      noSlowModule = (NoSlowModule)moduleRegistry.getModule(
         NoSlowModule.class
      );
   }
}
