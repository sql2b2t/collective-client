package me.collective.mixin;

import me.collective.client.managers.RotationManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public class InventoryScreenMixin {

   @Inject(method = "renderEntityInInventoryFollowsMouse", at = @At("HEAD"))
   private static void beginInventoryEntityRender(
      GuiGraphics graphics, int left, int top, int right, int bottom, int size, float verticalOffset, float mouseX, float mouseY, LivingEntity entity, CallbackInfo callback
   ) {
      RotationManager.renderingInventory = true;
   }

   @Inject(method = "renderEntityInInventoryFollowsMouse", at = @At("RETURN"))
   private static void endInventoryEntityRender(
      GuiGraphics graphics, int left, int top, int right, int bottom, int size, float verticalOffset, float mouseX, float mouseY, LivingEntity entity, CallbackInfo callback
   ) {
      RotationManager.renderingInventory = false;
   }
}
