package me.collective.mixin;

import me.collective.client.access.MobEffectDurationAccess;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEffectInstance.class)
public class MobEffectInstanceMixin implements MobEffectDurationAccess {
   @Shadow
   private int duration;
   @Unique
   private int collective$initialDuration;

   @Inject(method = "<init>(Lnet/minecraft/core/Holder;IIZZZLnet/minecraft/world/effect/MobEffectInstance;)V", at = @At("TAIL"))
   private void collective$captureInitialDuration(
      Holder effect, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon, MobEffectInstance hiddenEffect, CallbackInfo ci
   ) {
      this.collective$initialDuration = duration;
   }

   @Inject(method = "setDetailsFrom", at = @At("TAIL"))
   private void collective$captureCopiedDuration(MobEffectInstance effect, CallbackInfo ci) {
      this.collective$initialDuration = effect.getDuration();
   }

   @Inject(method = "update", at = @At(value = "FIELD", target = "Lnet/minecraft/world/effect/MobEffectInstance;duration:I", opcode = 181, shift = Shift.AFTER))
   private void collective$captureUpdatedDuration(CallbackInfoReturnable<Boolean> ci) {
      this.collective$initialDuration = this.duration;
   }

   @Override
   public float collective$getDurationRatio() {
      if (this.collective$initialDuration <= 0) {
         return this.duration <= 0 ? 0.0F : 1.0F;
      }
      return (float)this.duration / this.collective$initialDuration;
   }
}
