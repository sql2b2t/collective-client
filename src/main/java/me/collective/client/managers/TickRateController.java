package me.collective.client.managers;

import me.collective.client.ClientAccess;
import me.collective.client.events.EventHandler;
import me.collective.client.events.LocalPlayerTickEvent;
import me.collective.client.events.ModuleToggleEvent;
import me.collective.client.modules.Module;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Mth;

public class TickRateController implements ClientAccess {
   private final ConcurrentHashMap<Module, Boolean> moduleReleaseStates = new ConcurrentHashMap<>();
   private float multiplier;

   public TickRateController() {
      this.multiplier = 1.0F;
      ClientAccess.eventBus.subscribe(this);
   }

   @EventHandler
   private void onModuleToggle(ModuleToggleEvent event) {
      Module module = event.getModule();
      if (!module.isToggled()) {
         Module moduleToRelease = event.getModule();
         this.releaseMultiplier(moduleToRelease);
      }
   }

   public void requestMultiplier(Module module, float requestedMultiplier) {
      this.moduleReleaseStates.compute(module, (keyModule, previousReleased) -> false);
      this.multiplier = requestedMultiplier;
   }

   public float getMultiplier() {
      return this.multiplier;
   }

   public void releaseMultiplier(Module module) {
      this.moduleReleaseStates.compute(module, (keyModule, previousReleased) -> {
         if (Boolean.FALSE.equals(previousReleased)) {
            this.multiplier = 1.0F;
         }
         return true;
      });
   }

   public static class PlayerTicks extends TickRateController {
      private float fractionalTicks;

      @EventHandler(priority = -200)
      private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
         float rate = this.getMultiplier();
         if (rate <= 1.0F) {
            return;
         }

         this.fractionalTicks += rate - (int)rate;
         for (int extraTicks = 0;
            extraTicks < Math.floor(rate) + Math.floor(this.fractionalTicks) - 1.0D;
            extraTicks++
         ) {
            PlayerTickExecutor.runExtraTick();
         }

         float fractionSnapshot = this.fractionalTicks;
         if (fractionSnapshot >= 1.0F) {
            float fractionToReduce = this.fractionalTicks;
            float reducedFraction = fractionToReduce - 1.0F;
            float maximumFraction = 1.0F;
            this.fractionalTicks = Mth.clamp(reducedFraction, 0.0F, maximumFraction);
         }
      }
   }
}
