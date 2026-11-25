package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import me.collective.client.modules.client.AntiCheatModule;
import me.collective.client.modules.combat.AutoClickerModule;
import me.collective.client.modules.player.AutoToolModule;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.utils.world.BlockBreakingState;
import me.collective.client.utils.network.PacketSender;
import me.collective.client.utils.inventory.InventoryUtils;
import me.collective.client.utils.inventory.ItemUtils;
import me.collective.client.modules.player.InventoryTweaksModule;
import me.collective.client.events.MultiPlayerGameModeDestroyBlockEvent;
import me.collective.client.events.GameModeUseItemPacketEvent;
import me.collective.client.events.MultiPlayerGameModeReleaseUsingItemEvent;
import me.collective.client.events.MultiPlayerGameModeStartDestroyBlockEvent;
import me.collective.client.events.MultiPlayerGameModeUseItemEvent;
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent;
import me.collective.client.modules.player.NoInteractModule;
import me.collective.client.modules.player.SpeedMineModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeInteractionMixin implements ClientAccess, BlockBreakingState {
   private static AutoToolModule autoToolModule;
   private static AutoClickerModule autoClickerModule;
   private static NoInteractModule noInteractModule;
   private static InventoryTweaksModule inventoryTweaksModule;
   private static AntiCheatModule antiCheatModule;
   private static SpeedMineModule speedmine;
   private static AutoToolModule autoToolModule() {
      if (autoToolModule == null) {
         autoToolModule = ClientContext.getModule(AutoToolModule.class);
      }
      return autoToolModule;
   }
   private static AutoClickerModule autoClickerModule() {
      if (autoClickerModule == null) {
         autoClickerModule = ClientContext.getModule(AutoClickerModule.class);
      }
      return autoClickerModule;
   }
   private static NoInteractModule noInteractModule() {
      if (noInteractModule == null) {
         noInteractModule = ClientContext.getModule(NoInteractModule.class);
      }
      return noInteractModule;
   }
   private static InventoryTweaksModule inventoryTweaksModule() {
      if (inventoryTweaksModule == null) {
         inventoryTweaksModule = ClientContext.getModule(InventoryTweaksModule.class);
      }
      return inventoryTweaksModule;
   }
   private static AntiCheatModule antiCheatModule() {
      if (antiCheatModule == null) {
         antiCheatModule = ClientContext.getModule(AntiCheatModule.class);
      }
      return antiCheatModule;
   }
   private static SpeedMineModule speedmine() {
      if (speedmine == null) {
         speedmine = ClientContext.getModule(SpeedMineModule.class);
      }
      return speedmine;
   }
   @Shadow
   private boolean isDestroying;
   @Shadow
   private float destroyProgress;
   @Shadow
   private BlockPos destroyBlockPos;
   @Shadow
   private int destroyDelay;

   @Shadow
   public abstract void handleInventoryMouseClick(int containerId, int slotId, int mouseButton, ClickType clickType, Player actingPlayer);

   @Shadow
   protected abstract void startPrediction(ClientLevel level, PredictiveAction action);

   @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
   private void postDestroyBlockEvent(
      BlockPos blockPos, CallbackInfoReturnable<Boolean> callback
   ) {
      MultiPlayerGameModeDestroyBlockEvent event = new MultiPlayerGameModeDestroyBlockEvent(blockPos);
      ClientAccess.eventBus.post(
         event
      );
      if (event.isCancelled()) {
         callback.setReturnValue(false);
         callback.cancel();
      }
   }

   @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
   private void postStartDestroyBlockEvent(
      BlockPos blockPos, Direction face, CallbackInfoReturnable<Boolean> callback
   ) {
      MultiPlayerGameModeStartDestroyBlockEvent event = new MultiPlayerGameModeStartDestroyBlockEvent(blockPos, face);
      ClientAccess.eventBus.post(
         event
      );
      if (event.isCancelled()) {
         callback.setReturnValue(true);
         callback.cancel();
      }
   }

   @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true)
   public void applyInventoryClickTweaks(
      int containerId, int slotId, int mouseButton, ClickType clickType, Player actingPlayer, CallbackInfo callback
   ) {
      if (inventoryTweaksModule().isToggled()) {
         AbstractContainerMenu menu = actingPlayer.containerMenu;
         if (inventoryTweaksModule().fastArmorSetting.getValue()
            && mouseButton == 1
            && clickType == ClickType.QUICK_MOVE
            && slotId >= 0
            && slotId < menu.slots.size()) {
            Slot clickedSlot = menu.getSlot(slotId);
            if (clickedSlot.getItem().has(DataComponents.EQUIPPABLE) || clickedSlot.getItem().is(Items.ELYTRA)) {
               InventoryUtils.swapSlotsWithPickup(
                  slotId,
                  8
                     - ClientAccess.collectiveMinecraft.player
                        .getEquipmentSlotForItem(clickedSlot.getItem())
                        .getIndex()
               );
               callback.cancel();
               return;
            }
         }

         if (inventoryTweaksModule().xCarryTweaksSetting.getValue()
            && mouseButton == 1
            && clickType == ClickType.QUICK_MOVE
            && slotId > 4
            && menu instanceof InventoryMenu) {
            for (int craftingSlotIndex = 0;
               craftingSlotIndex
                  < 4;
               craftingSlotIndex++
            ) {
               ItemStack craftingStack = ClientAccess.collectiveMinecraft.player
                  .inventoryMenu
                  .getCraftSlots()
                  .getItems()
                  .get(craftingSlotIndex);
               if (craftingStack.isEmpty()) {
                  this.handleInventoryMouseClick(
                     containerId,
                     slotId,
                     0,
                     ClickType.PICKUP,
                     ClientAccess.collectiveMinecraft.player
                  );
                  this.handleInventoryMouseClick(
                     containerId,
                     craftingSlotIndex + 1,
                     0,
                     ClickType.PICKUP,
                     ClientAccess.collectiveMinecraft.player
                  );
                  callback.cancel();
                  return;
               }
            }
         }
      }
   }

   @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
   private void postUseItemOnAndApplyNoInteract(
      LocalPlayer actingPlayer, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> callback
   ) {
      MultiPlayerGameModeUseItemOnEvent event = new MultiPlayerGameModeUseItemOnEvent(hit, hand);
      ClientAccess.eventBus.post(
         event
      );
      if (event.isCancelled()) {
         callback.setReturnValue(InteractionResult.FAIL);
         callback.cancel();
      } else {
         hand = NoInteractModule.selectUsableHand();
         ItemStack heldStack = ClientAccess.collectiveMinecraft.player
            .getItemInHand(
               NoInteractModule.selectUsableHand()
            );
         if (noInteractModule().shouldBlockInteraction(
            heldStack, hit.getBlockPos()
         )) {
            if (noInteractModule().modeSetting.getValue()
               == NoInteractModule.ModeChoice.SHIFT) {
               noInteractModule().shouldStopSneaking = !ClientContext.serverStateManager.isServerSneaking();
               ClientContext.playerStateTracker
                  .setSneaking(true);
            } else {
               callback.setReturnValue(InteractionResult.FAIL);
               callback.cancel();
            }

            ClientAccess.collectiveMinecraft.gameMode
               .useItem(ClientAccess.collectiveMinecraft.player, hand);
         }
      }
   }

   // Mojang leaves the use-on packet prediction lambda unnamed.
   @ModifyReturnValue(method = "method_41933(Lorg/apache/commons/lang3/mutable/MutableObject;Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;I)Lnet/minecraft/network/protocol/Packet;", at = @At("RETURN"))
   private Packet<?> markImportantUseItemOnPacket(
      Packet<?> packet, @Local(argsOnly = true) LocalPlayer actingPlayer
   ) {
      if (actingPlayer.isUsingItem()) {
         PacketSender.markImportant(packet);
      }
      return packet;
   }

   // Mojang leaves the use-item packet prediction lambda unnamed.
   @Inject(method = "method_41929(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/entity/player/Player;Lorg/apache/commons/lang3/mutable/MutableObject;I)Lnet/minecraft/network/protocol/Packet;", at = @At("TAIL"))
   private void markImportantUseItemPacket(
      InteractionHand hand, Player actingPlayer, MutableObject result, int sequence, CallbackInfoReturnable<Packet<?>> callback, @Local ServerboundUseItemPacket packet
   ) {
      if (actingPlayer.isUsingItem() || ItemUtils.isFood(actingPlayer.getItemInHand(hand))) {
         PacketSender.markImportant(packet);
      }
   }

   @Inject(method = "useItemOn", at = @At("RETURN"))
   private void restoreSneakingAfterUseItemOn(
      LocalPlayer actingPlayer, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> callback
   ) {
      if (noInteractModule().shouldStopSneaking
         )
       {
         ClientContext.playerStateTracker
            .setSneaking(false);
         noInteractModule().shouldStopSneaking = false;
      }
   }

   @Inject(method = "releaseUsingItem", at = @At("HEAD"), cancellable = true)
   private void postReleaseUsingItemEvent(Player actingPlayer, CallbackInfo callback) {
      MultiPlayerGameModeReleaseUsingItemEvent event = new MultiPlayerGameModeReleaseUsingItemEvent();
      ClientAccess.eventBus.post(
         event
      );
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
   private void postUseItemEvent(
      Player actingPlayer, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback
   ) {
      MultiPlayerGameModeUseItemEvent event = new MultiPlayerGameModeUseItemEvent(hand);
      ClientAccess.eventBus.post(
         event
      );
      if (event.isCancelled()) {
         callback.cancel();
         callback.setReturnValue(InteractionResult.FAIL);
      }
   }

   @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
   private void protectFriendsFromAttack(
      Player actingPlayer, Entity attackedEntity, CallbackInfo callback
   ) {
      if (autoClickerModule().shouldProtectFriend(
            attackedEntity)
         && actingPlayer == ClientAccess.collectiveMinecraft.player) {
         callback.cancel();
      }
   }

   @WrapWithCondition(method = "stopDestroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
   private boolean allowStopDestroyBlockPacket(ClientPacketListener connection, Packet packet) {
      return !speedmine().isToggled();
   }

   @Inject(method = "continueDestroyBlock", at = @At("HEAD"))
   private void clearDestroyDelayForSpeedMine(
      BlockPos blockPos, Direction face, CallbackInfoReturnable<Boolean> callback
   ) {
      if (speedmine().isToggled()) {
         this.destroyDelay = 0;
      }
   }

   // Mojang leaves the use-item packet prediction lambda unnamed.
   @ModifyExpressionValue(method = "method_41929(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/entity/player/Player;Lorg/apache/commons/lang3/mutable/MutableObject;I)Lnet/minecraft/network/protocol/Packet;", at = @At(value = "NEW", target = "(Lnet/minecraft/world/InteractionHand;IFF)Lnet/minecraft/network/protocol/game/ServerboundUseItemPacket;"))
   private ServerboundUseItemPacket modifyPredictedUseItemPacket(ServerboundUseItemPacket packet) {
      GameModeUseItemPacketEvent event = new GameModeUseItemPacketEvent(packet);
      ClientAccess.eventBus.post(
         event
      );
      return event.isCancelled() ? event.getPacket() : packet;
   }

   @ModifyExpressionValue(method = "continueDestroyBlock", at = @At(value = "CONSTANT", args = "floatValue=1.0", ordinal = 4), require = 0)
   private float modifyContinueDestroyBlockDamageThreshold(float originalThreshold) {
      return autoToolModule().isBlockDamageOverrideActive()
         ? autoToolModule().blockDamageSetting.getValue()
         : originalThreshold;
   }

   // Mojang leaves the block-break prediction lambda unnamed.
   @ModifyExpressionValue(method = "method_41930(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;I)Lnet/minecraft/network/protocol/Packet;", at = @At(value = "CONSTANT", args = "floatValue=1.0"), require = 0)
   private float modifyPredictedBlockDamageThreshold(float originalThreshold) {
      return autoToolModule().isBlockDamageOverrideActive()
         ? autoToolModule().blockDamageSetting.getValue()
         : originalThreshold;
   }

   @Inject(
      method = "startDestroyBlock",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;startPrediction(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/multiplayer/prediction/PredictiveAction;)V",
         ordinal = 1,
         shift = Shift.AFTER
      )
   )
   private void finishBlockAfterStartPrediction(
      BlockPos blockPos, Direction face, CallbackInfoReturnable<Boolean> callback, @Local BlockState blockState
   ) {
      float damageThreshold = autoToolModule().blockDamageSetting.getValue();
      if (autoToolModule().isBlockDamageOverrideActive()) {
         if (blockState.getDestroyProgress(
                  ClientAccess.collectiveMinecraft.player,
                  ClientAccess.collectiveMinecraft.player.level(),
                  blockPos
               )
               >= damageThreshold
            && !blockState.isAir()) {
            this.startPrediction(
               ClientAccess.collectiveMinecraft.level,
               sequence -> new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, blockPos, face, sequence)
            );
            if (antiCheatModule().is2b2tEnabled()) {
               this.startPrediction(
                  ClientAccess.collectiveMinecraft.level,
                  sequence -> new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, blockPos, face, sequence)
               );
            }
         }
      }
   }

   @ModifyExpressionValue(method = "continueDestroyBlock", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I", ordinal = 0))
   private int modifyDestroyDelayForAutoTool(int originalDelay) {
      return autoToolModule().isToggled()
            && autoToolModule().noBreakDelaySetting.getValue()
         ? 0
         : originalDelay;
   }

   @Override
   public boolean isBreakingBlock() {
      return this.isDestroying;
   }

   @Override
   public void setBreakingBlock(boolean breaking) {
      this.isDestroying = breaking;
   }

   @Override
   public float getBreakingProgress() {
      return this.destroyProgress;
   }

   @Override
   public void setBreakingProgress(float progress) {
      this.destroyProgress = progress;
   }

   @Override
   public BlockPos getCurrentBreakingBlock() {
      return this.destroyBlockPos;
   }

   @Override
   public void setCurrentBreakingBlock(BlockPos blockPos) {
      this.destroyBlockPos = blockPos;
   }
}
