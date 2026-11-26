package me.collective.mixin;

import me.collective.client.modules.render.XrayModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockStateBase.class)
public class BlockBehaviourBlockStateBaseMixin {
   @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true)
   private void overrideXrayShadeBrightness(
      BlockGetter level, BlockPos blockPos, CallbackInfoReturnable<Float> callback
   ) {
      if (XrayModule.getInstance()
            .isToggled()
         && XrayModule.getInstance()
            .shouldHideUnselectedBlocks()) {
         callback.cancel();
         callback.setReturnValue(1.0F);
      }
   }
}
