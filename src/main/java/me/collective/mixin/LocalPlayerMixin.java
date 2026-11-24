package me.collective.mixin;

import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.events.ItemUseEvent;
import me.collective.client.modules.movement.SprintModule;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
   private static SprintModule sprintModule;
   private static SprintModule sprintModule() {
      if (sprintModule == null) {
         sprintModule = ClientContext.getModule(SprintModule.class);
      }
      return sprintModule;
   }
   @Unique
   private boolean aiStepInputActive;
   @Unique
   private boolean itemUseSlowdownCancelled;

   @Inject(
      method = "aiStep",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;input:Lnet/minecraft/client/player/ClientInput;", ordinal = 4)
   )
   private void onAiStepBeforeInput(CallbackInfo callback) {
      ItemUseEvent.Slowdown event = new ItemUseEvent.Slowdown();
      ClientAccess.eventBus
         .post(event);
      this.itemUseSlowdownCancelled = event.isCancelled();
      this.aiStepInputActive = true;
      if (sprintModule().isToggled()
         && sprintModule().shouldDeferToVanillaSprinting()) {
         SprintModule.forceSprintKeyDown = true;
      }
   }

   @Inject(method = "aiStep", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;mayfly:Z"))
   private void clearSprintKeyOverride(CallbackInfo callback) {
      SprintModule.forceSprintKeyDown = false;
   }

   @Inject(method = "aiStep", at = @At("TAIL"))
   private void finishAiStepInput(CallbackInfo callback) {
      this.aiStepInputActive = false;
   }

   @Inject(method = "isUsingItem", at = @At("HEAD"), cancellable = true)
   private void suppressItemUseDuringAiStep(CallbackInfoReturnable<Boolean> callback) {
      if (this.itemUseSlowdownCancelled
         && this.aiStepInputActive) {
         callback.setReturnValue(false);
      }
   }
}
