package me.collective.mixin;

import me.collective.client.ClientAccess;
import me.collective.client.events.FishingHookPullEntityEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHook.class)
public class FishingHookMixin implements ClientAccess {
   @Inject(method = "pullEntity", at = @At("HEAD"), cancellable = true)
   private void postFishingHookPullEvent(Entity entity, CallbackInfo callback) {
      FishingHookPullEntityEvent event = new FishingHookPullEntityEvent(entity);
      ClientAccess.eventBus.post(
         event
      );
      if (event.isCancelled()) {
         callback.cancel();
      }
   }
}
