package me.collective.client.modules.movement

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.events.InventoryActionEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.MultiPlayerGameModeHandleInventoryMouseClickEvent
import me.collective.client.managers.RotationManager
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.inventory.NoSlowInventoryMode
import me.collective.client.integrations.ProtocolCompatibility
import me.collective.mixin.LocalPlayerAccessor
import java.util.ArrayDeque
import java.util.Queue
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.entity.player.Input
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack

class NoSlowInventoryGrimStrictMode(noSlow: NoSlowModule) : NoSlowInventoryMode(noSlow) {
   private val pendingActions: Queue<Runnable> = ArrayDeque()
   private var flushPending: Boolean = false
   private var replayingAction: Boolean = false
   private var inputActive: Boolean = false
   private var inventoryInputSuppressed: Boolean = false

   private fun flushQueuedActions() {
      if (this.pendingActions.isEmpty()) return
      var attempts = 0L
      while (!this.pendingActions.isEmpty()) {
         if (++attempts > 50L) break
         val action: Runnable? = this.pendingActions.poll()
         if (action != null) this.runReplayedAction(action)
      }
      this.pendingActions.clear()
   }

   private fun isDelayablePacket(packet: Packet<*>): Boolean {
      return packet is ServerboundUseItemPacket ||
         packet is ServerboundSwingPacket ||
         packet is PosRot && RotationManager.isSilentRotationMode()
   }

   override fun onPacketSend(event: PacketEvent.Send) {
      if (this.replayingAction) return
      if (this.usesLegacyClickProtocol()) return
      val packet: Packet<*> = event.packet
      if (this.isDelayablePacket(packet) && this.inputActive && !this.pendingActions.isEmpty()) {
         this.queueOrRunAction {
            PacketSender.send(event.packet)
         }
         event.cancel()
      }
      if (event.packet is ServerboundContainerClosePacket && this.inputActive) {
         val closePacket: ServerboundContainerClosePacket = event.packet as ServerboundContainerClosePacket
         this.queueOrRunAction { PacketSender.send(closePacket) }
         event.cancel()
      }
      if (event.packet is ServerboundPlayerCommandPacket) {
         val command: ServerboundPlayerCommandPacket = event.packet as ServerboundPlayerCommandPacket
         if (command.action == Action.START_FALL_FLYING) {
            this.inputActive = false
            this.flushQueuedActions()
         }
      }
   }

   override fun onInventoryClickIntercept(event: MultiPlayerGameModeHandleInventoryMouseClickEvent.AfterStackHash) {
      if (this.usesLegacyClickProtocol()) return
      if (this.replayingAction || !this.inputActive) return
      val containerId: Int = event.containerId
      val slotId: Int = event.slotId
      val button: Int = event.button
      val clickType: ContainerInput = event.clickType
      this.queueOrRunAction {
         ClientAccess.collectiveMinecraft.gameMode!!.handleContainerInput(containerId, slotId, button, clickType, ClientAccess.collectiveMinecraft.player!!)
      }
      event.cancel()
   }

   override fun beforeMenuClick(event: MultiPlayerGameModeHandleInventoryMouseClickEvent.BeforeContainerClick) {
      if (this.usesLegacyClickProtocol()) return
      if (this.replayingAction) {
         event.cancel()
      }
   }

   override fun onTickStart(event: LocalPlayerPreTickEvent) {
      if (this.flushPending) {
         this.flushQueuedActions()
         this.flushPending = false
      }
      this.inputActive = this.hasPlayerInput()
      var keepPendingFlush: Boolean = false
      if (this.inputActive && ClientAccess.collectiveMinecraft.gui.screen() is AbstractContainerScreen<*>) {
         val cursorStack: ItemStack = InventoryUtils.getCarriedStack()
         keepPendingFlush = !cursorStack.isEmpty
      }
      if (!keepPendingFlush) this.flushPending = !this.pendingActions.isEmpty()
      if (this.flushPending) {
         ClientContext.inputOverrides.requestReset()
         ClientAccess.collectiveMinecraft.player!!.setSprinting(false)
      }
   }

   override fun onInventoryAction(event: InventoryActionEvent) {
      if (this.usesLegacyClickProtocol() && !this.inventoryInputSuppressed) {
         val emptyInput: Input = Input(false, false, false, false, false, false, false)
         PacketSender.send(ServerboundPlayerInputPacket(emptyInput))
         PacketSender.sendPlayerCommand(Action.STOP_SPRINTING)
         (ClientAccess.collectiveMinecraft.player!! as LocalPlayerAccessor).`collective$setLastPlayerInput`(emptyInput)
         (ClientAccess.collectiveMinecraft.player!! as LocalPlayerAccessor).`collective$setLastSprinting`(false)
         ClientContext.inputOverrides.blockSprint()
         this.inventoryInputSuppressed = true
      }
   }

   private fun canQueueInCurrentScreen(): Boolean {
      if (ClientAccess.collectiveMinecraft.gui.screen() is AbstractContainerScreen<*>) {
         val screen: AbstractContainerScreen<*> = ClientAccess.collectiveMinecraft.gui.screen() as AbstractContainerScreen<*>
         if (!this.noSlow.containersSetting.value) {
            return screen.menu === ClientAccess.collectiveMinecraft.player!!.inventoryMenu
         }
      }
      return true
   }

   private fun hasPlayerInput(): Boolean {
      val input: Input = ClientAccess.collectiveMinecraft.player!!.input.keyPresses
      if (input.forward()) return true
      if (input.backward()) return true
      if (input.left()) return true
      if (input.right()) return true
      if (input.jump()) return true
      if (input.shift()) return true
      return input.sprint()
   }

   @Synchronized
   private fun runReplayedAction(action: Runnable) {
      this.replayingAction = true
      action.run()
      this.replayingAction = false
   }

   override fun onPlayerAiStep() {
      if (this.flushPending || this.inventoryInputSuppressed) {
         ClientAccess.collectiveMinecraft.player!!.setSprinting(false)
      }
   }

   fun isReplayingAction(): Boolean {
      return this.replayingAction
   }

   override fun onTickBeforePassengerCheck() {
      this.inputActive = this.hasPlayerInput()
      this.inventoryInputSuppressed = false
   }

   private fun usesLegacyClickProtocol(): Boolean {
      return ProtocolCompatibility.getTargetVersion().olderEq_1_21_5()
   }

   @Synchronized
   fun queueOrRunAction(action: Runnable) {
      if (this.replayingAction) return
      if (this.noSlow.guiMoveSetting.value) {
         if (this.canQueueInCurrentScreen()) {
            this.pendingActions.add(action)
            return
         }
      }
      this.runReplayedAction(action)
   }
}
