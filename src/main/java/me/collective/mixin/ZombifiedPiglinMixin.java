package me.collective.mixin;

import me.collective.client.utils.ElapsedTimer;
import me.collective.client.utils.combat.ZombifiedPiglinAttackState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombifiedPiglin.class)
public abstract class ZombifiedPiglinMixin extends Mob implements ZombifiedPiglinAttackState {
   @Unique
   private ElapsedTimer stopwatch;

   protected ZombifiedPiglinMixin(EntityType entityType, Level level) {
      super(entityType, level);
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void initializeAttackTimer(
      EntityType entityType, Level level, CallbackInfo callback
   ) {
      this.stopwatch = new ElapsedTimer();
   }

   @Override
   public boolean collective$isAttacking() {
      if (this.isAggressive() || this.swingTime > 0) {
         this.stopwatch
            .reset();
      }

      return !this.stopwatch.hasElapsed(1000L);
   }
}
