package me.collective.mixin;

import me.collective.client.ClientContext;
import me.collective.client.managers.ModuleRegistry;
import me.collective.client.modules.movement.NoSlowModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SlimeBlock.class)
public class SlimeBlockMixin {
   private static NoSlowModule noslow;
   private static NoSlowModule noslow() {
      if (noslow == null) {
         noslow = ClientContext.getModule(NoSlowModule.class);
      }
      return noslow;
   }

   @Inject(method = "stepOn", at = @At("HEAD"), cancellable = true)
   private void suppressSlimeStepEffect(
      Level level, BlockPos blockPos, BlockState blockState, Entity entity, CallbackInfo callback
   ) {
      if (noslow().isToggled() && noslow().slimeSetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(method = "bounceUp", at = @At("HEAD"), cancellable = true)
   private void suppressSlimeBounce(Entity entity, CallbackInfo callback) {
      if (noslow().isToggled() && noslow().slimeSetting.getValue() && noslow().noSlimeBounceSetting.getValue()) {
         callback.cancel();
      }
   }

   @Unique
   private static void collective$bindSlimeBlockModules(ModuleRegistry moduleRegistry) {
      noslow = (NoSlowModule)moduleRegistry.getModule(NoSlowModule.class);
   }
}
