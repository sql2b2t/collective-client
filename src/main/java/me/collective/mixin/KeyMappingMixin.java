package me.collective.mixin;

import me.collective.client.modules.movement.SprintModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {
   @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
   public void overrideSprintKeyState(CallbackInfoReturnable<Boolean> callback) {
      if ((Object)this == Minecraft.getInstance().options.keySprint
         && SprintModule.forceSprintKeyDown) {
         callback.setReturnValue(true);
      }
   }
}
