package me.collective.mixin;

import me.collective.client.managers.PlayerTickExecutor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Consumable.class)
public class ConsumableMixin {

   @Inject(method = "emitParticlesAndSounds", at = @At("HEAD"), cancellable = true)
   private void cancelExtraTickConsumptionEffects(
      RandomSource random, LivingEntity entity, ItemStack stack, int remainingTicks, CallbackInfo callback
   ) {
      if (PlayerTickExecutor.isRunningExtraTick()) {
         callback.cancel();
      }
   }

   @Inject(method = "shouldEmitParticlesAndSounds", at = @At("HEAD"), cancellable = true)
   private void suppressExtraTickConsumptionEffects(int remainingTicks, CallbackInfoReturnable<Boolean> callback) {
      if (PlayerTickExecutor.isRunningExtraTick()) {
         callback.setReturnValue(false);
      }
   }

}
