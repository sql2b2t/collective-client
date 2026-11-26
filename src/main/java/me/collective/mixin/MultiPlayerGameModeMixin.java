package me.collective.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.collective.client.ClientAccess;
import me.collective.client.events.MultiPlayerGameModeHandleInventoryMouseClickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
   @Shadow
   @Final
   private Minecraft minecraft;

   @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true)
   private void onInventoryMouseClickHead(
      int containerId, int slotId, int mouseButton, ContainerInput clickType, Player actingPlayer, CallbackInfo callback
   ) {
      if (this.minecraft.player == actingPlayer) {
         MultiPlayerGameModeHandleInventoryMouseClickEvent event = new MultiPlayerGameModeHandleInventoryMouseClickEvent(containerId, slotId, mouseButton, clickType);
         ClientAccess.eventBus
            .post(event);
         if (event.isCancelled()) {
            callback.cancel();
         }
      }
   }

   @Inject(method = "handleInventoryMouseClick", at = @At("TAIL"), cancellable = true)
   private void onInventoryMouseClickTail(
      int containerId, int slotId, int mouseButton, ContainerInput clickType, Player actingPlayer, CallbackInfo callback
   ) {
      if (this.minecraft.player == actingPlayer) {
         MultiPlayerGameModeHandleInventoryMouseClickEvent.After event = new MultiPlayerGameModeHandleInventoryMouseClickEvent.After(
            containerId, slotId, mouseButton, clickType
         );
         ClientAccess.eventBus
            .post(event);
         if (event.isCancelled()) {
            callback.cancel();
         }
      }
   }

   @Inject(
      method = "handleInventoryMouseClick",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/HashedStack;create(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/network/HashedPatchMap$HashGenerator;)Lnet/minecraft/network/HashedStack;",
         shift = Shift.AFTER,
         ordinal = 1
      ),
      cancellable = true
   )
   private void onInventoryMouseClickAfterStackHash(
      int containerId, int slotId, int mouseButton, ContainerInput clickType, Player actingPlayer, CallbackInfo callback
   ) {
      if (this.minecraft.player == actingPlayer) {
         MultiPlayerGameModeHandleInventoryMouseClickEvent.AfterStackHash event = new MultiPlayerGameModeHandleInventoryMouseClickEvent.AfterStackHash(
            containerId, slotId, mouseButton, clickType
         );
         ClientAccess.eventBus
            .post(event);
         if (event.isCancelled()) {
            callback.cancel();
         }
      }
   }

   @WrapWithCondition(
      method = "handleInventoryMouseClick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;clicked(IILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V")
   )
   private boolean shouldApplyContainerClick(
      AbstractContainerMenu menu, int slotId, int mouseButton, ContainerInput clickType, Player actingPlayer
   ) {
      if (this.minecraft.player == actingPlayer) {
         MultiPlayerGameModeHandleInventoryMouseClickEvent.BeforeContainerClick event = new MultiPlayerGameModeHandleInventoryMouseClickEvent.BeforeContainerClick();
         ClientAccess.eventBus
            .post(event);
         return !event.isCancelled();
      } else {
         return true;
      }
   }
}
