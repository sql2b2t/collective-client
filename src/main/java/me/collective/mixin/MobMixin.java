package me.collective.mixin;

import me.collective.client.ClientContext;
import me.collective.client.modules.movement.EntityControlModule;
import me.collective.client.managers.ModuleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Strider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AbstractHorse.class, Pig.class, Strider.class})
public class MobMixin {
   private static EntityControlModule entityControlModule;
   private static EntityControlModule entityControlModule() {
      if (entityControlModule == null) {
         entityControlModule = ClientContext.getModule(EntityControlModule.class);
      }
      return entityControlModule;
   }

   @Inject(method = "isSaddled", at = @At("HEAD"), cancellable = true)
   private void forceSaddledForEntityControl(CallbackInfoReturnable<Boolean> callback) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (entityControlModule().isToggled()
         && player != null
         && ((Mob)(Object)this).getPassengers().contains(player)) {
         callback.setReturnValue(true);
      }
   }

   @Unique
   private static void collective$bindMobModules(ModuleRegistry moduleRegistry) {
      entityControlModule = (EntityControlModule)moduleRegistry.getModule(
         EntityControlModule.class
      );
   }
}
