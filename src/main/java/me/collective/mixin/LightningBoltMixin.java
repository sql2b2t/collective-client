package me.collective.mixin;

import me.collective.client.render.ClientLightningState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningBolt.class)
public final class LightningBoltMixin implements ClientLightningState {
   @Unique
   private boolean clientLightning;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void initializeClientLightningState(
      EntityType entityType, Level level, CallbackInfo callback
   ) {
      this.clientLightning = false;
   }

   @Override
   public boolean collective$isClient() {
      return this.clientLightning;
   }

   @Override
   public void collective$setClient(boolean isClientLightning) {
      this.clientLightning = isClientLightning;
   }
}
