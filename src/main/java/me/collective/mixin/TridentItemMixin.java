package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.collective.client.ClientContext;
import me.collective.client.managers.ModuleRegistry;
import me.collective.client.modules.exploit.TridentModule;
import net.minecraft.world.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(TridentItem.class)
public class TridentItemMixin {
   private static TridentModule tridentModule;
   private static TridentModule tridentModule() {
      if (tridentModule == null) {
         tridentModule = ClientContext.getModule(TridentModule.class);
      }
      return tridentModule;
   }

   @ModifyExpressionValue(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"))
   private boolean allowRiptideOnRelease(boolean inWaterOrRain) {
      return inWaterOrRain
         || tridentModule().isAlwaysRiptideEnabled();
   }

   @ModifyExpressionValue(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"))
   private boolean allowRiptideOnUse(boolean inWaterOrRain) {
      return inWaterOrRain
         || tridentModule().isAlwaysRiptideEnabled();
   }

   @ModifyArgs(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;push(DDD)V"))
   private void scaleRiptidePush(Args arguments) {
      arguments.set(
         0,
         arguments.<Double>get(0)
            * tridentModule().getBoostMultiplier()
      );
      arguments.set(
         1,
         arguments.<Double>get(1)
            * tridentModule().getBoostMultiplier()
      );
      arguments.set(
         2,
         arguments.<Double>get(2)
            * tridentModule().getBoostMultiplier()
      );
   }

   @ModifyExpressionValue(method = "releaseUsing", at = @At(value = "CONSTANT", args = "intValue=10"))
   private int removeMinimumChargeTime(int originalChargeTime) {
      return tridentModule().isToggled() ? 0 : originalChargeTime;
   }

   @Unique
   private static void collective$bindTridentItemModules(ModuleRegistry moduleRegistry) {
      tridentModule = (TridentModule)moduleRegistry.getModule(
         TridentModule.class
      );
   }
}
