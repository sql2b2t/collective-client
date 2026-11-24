package me.collective.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.collective.client.events.AbstractContainerMenuMoveItemStackToEvent;
import me.collective.client.ClientAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {

   @WrapWithCondition(method = "moveItemStackTo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;setChanged()V"))
   private boolean postItemStackMoveEvent(Slot slot) {
      ClientAccess.eventBus
         .post(
            new AbstractContainerMenuMoveItemStackToEvent(slot)
         );
      return true;
   }
}
