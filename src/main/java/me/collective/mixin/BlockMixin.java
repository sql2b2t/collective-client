package me.collective.mixin;

import me.collective.client.ClientContext;
import me.collective.client.managers.ModuleRegistry;
import me.collective.client.modules.movement.NoSlowModule;
import me.collective.client.modules.render.XrayModule;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockMixin {
   private static NoSlowModule noslow;
   private static NoSlowModule noslow() {
      if (noslow == null) {
         noslow = ClientContext.getModule(NoSlowModule.class);
      }
      return noslow;
   }

   @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
   private static void overrideXrayFaceVisibility(
      BlockState blockState, BlockState adjacentState, Direction face, CallbackInfoReturnable<Boolean> callback
   ) {
      if (XrayModule.getInstance()
            .isToggled()
         && XrayModule.getInstance()
            .shouldHideUnselectedBlocks()) {
         callback.cancel();
         callback.setReturnValue(
            XrayModule.getInstance()
               .shouldRenderBlock(
                  blockState,
                  !adjacentState.canOcclude()
               )
         );
      }
   }

   @Inject(method = "getFriction", at = @At("HEAD"), cancellable = true)
   private void overrideNoSlowFriction(CallbackInfoReturnable<Float> callback) {
      if (noslow().isToggled()) {
         Block block = (Block)(Object)this;
         if (noslow().iceSetting.getValue() && (block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE)) {
            callback.setReturnValue(0.6F);
         }

         if (noslow().slimeSetting.getValue() && block == Blocks.SLIME_BLOCK) {
            callback.setReturnValue(0.6F);
         }

         if (noslow().honeySetting.getValue() && block == Blocks.HONEY_BLOCK) {
            callback.setReturnValue(0.6F);
         }
      }
   }

   @Unique
   private static void collective$bindBlockModules(ModuleRegistry moduleRegistry) {
      noslow = (NoSlowModule)moduleRegistry.getModule(NoSlowModule.class);
   }
}
