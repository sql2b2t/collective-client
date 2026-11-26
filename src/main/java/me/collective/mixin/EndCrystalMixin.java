package me.collective.mixin;

import me.collective.client.utils.combat.EndCrystalState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndCrystal.class)
public class EndCrystalMixin implements EndCrystalState {
   @Unique
   private long spawnTime;
   @Unique
   private boolean clientAttacked;

   @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", at = @At("RETURN"))
   private void initializeCrystalState(
      EntityType entityType, Level level, CallbackInfo callback
   ) {
      this.spawnTime = System.currentTimeMillis();
      this.clientAttacked = false;
   }

   @Override
   public long getSpawnTime() {
      return this.spawnTime;
   }

   @Override
   public boolean isClientAttacked() {
      return this.clientAttacked;
   }

   @Override
   public void setClientAttacked(boolean attacked) {
      this.clientAttacked = attacked;
   }
}
