package me.collective.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
   @Accessor("useItemRemaining")
   void setItemUseTimeLeft(int useItemRemaining);

   @Accessor("swimAmount")
   void collective$setLeaningPitch(float swimAmount);

   @Accessor("swimAmount")
   float collective$getLeaningPitch();

   @Accessor("swimAmountO")
   float collective$getLastLeaningPitch();

   @Accessor("swimAmountO")
   void collective$setLastLeaningPitch(float swimAmountO);

   @Accessor("fallFlyTicks")
   void collective$setGlidingTicks(int fallFlyTicks);

   @Invoker("getCurrentSwingDuration")
   int getSwingDuration();

   @Accessor("DATA_EFFECT_PARTICLES")
   static EntityDataAccessor collective$potion_swirls() {
      return null;
   }
}
