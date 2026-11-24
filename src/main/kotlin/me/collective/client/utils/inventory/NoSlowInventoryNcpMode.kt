package me.collective.client.utils.inventory

import me.collective.client.events.EventStage
import me.collective.client.utils.entity.PlayerStateTracker
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.network.PacketSender

import me.collective.client.utils.ElapsedTimer
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.InventoryActionEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.exploit.MultiTaskModule
import me.collective.client.modules.movement.NoSlowModule
import me.collective.client.settings.options.MultiTaskModeChoice
import me.collective.client.settings.options.NoSlowModeChoice

import me.collective.mixin.ServerboundMovePlayerPacketAccessor
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action

class NoSlowInventoryNcpMode(noSlow: NoSlowModule) : NoSlowInventoryMode(noSlow) {
   private val airborneTimer = ElapsedTimer()
   private var groundSpoofPending: Boolean = false
   private var positionOffsetSent: Boolean = false

   override fun onInventoryAction(event: InventoryActionEvent) {
      val guiMoveEnabled = this.noSlow.guiMoveSetting.value
      if (!guiMoveEnabled) return
      val guiModule: NoSlowModule = this.noSlow
      if (!guiModule.isGuiMoveAllowed()) return
      if (event.stage == EventStage.Pre) {
         val player = ClientAccess.collectiveMinecraft.player ?: return
         var releaseItem = true
         if (multiTask.mode == MultiTaskModeChoice.ALL) {
            releaseItem = this.noSlow.modeSetting.value == NoSlowModeChoice.CONSTANTIAM
         }
         if (releaseItem && !this.noSlow.releasedUsingItemThisTick) {
            val gameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode ?: return
            gameMode.releaseUsingItem(player)
            this.noSlow.releasedUsingItemThisTick = true
         }
         if (!MovementPhysics.isLocalPlayerMoving()) return
         if (ClientContext.playerStateTracker.getGroundedTicks() > 1 && !this.positionOffsetSent) {
            val x = player.x
            val offsetY = player.y + 0.0626
            val z = player.z
            PacketSender.sendPosition(x, offsetY, z, false)
            this.positionOffsetSent = true
            this.groundSpoofPending = true
         }
         val sprintPlayer: LocalPlayer = player
         if (sprintPlayer.isSprinting) {
            val stopSprinting = ServerboundPlayerCommandPacket(
               player, Action.STOP_SPRINTING
            )
            PacketSender.send(stopSprinting)
         }
         val shiftPlayer: LocalPlayer = player
         if (shiftPlayer.isShiftKeyDown) {
            val shiftState: PlayerStateTracker = ClientContext.playerStateTracker
            shiftState.setSneaking(false)
         }
      } else if (event.stage == EventStage.Post && MovementPhysics.isLocalPlayerMoving()) {
         val player = ClientAccess.collectiveMinecraft.player ?: return
         val sprintPlayer: LocalPlayer = player
         if (sprintPlayer.isSprinting) {
            val startSprinting = ServerboundPlayerCommandPacket(
               player, Action.START_SPRINTING
            )
            PacketSender.send(startSprinting)
         }
         val shiftPlayer: LocalPlayer = player
         if (shiftPlayer.isShiftKeyDown) {
            val shiftState: PlayerStateTracker = ClientContext.playerStateTracker
            shiftState.setSneaking(true)
         }
      }
   }

   override fun onTickStart(event: LocalPlayerPreTickEvent) {
      val groundPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      if (!groundPlayer.onGround()) {
         this.airborneTimer.reset()
      }
      this.positionOffsetSent = false
   }

   override fun onPacketSend(event: PacketEvent.Send) {
      val packet = event.packet as? ServerboundMovePlayerPacket ?: return
      val accessor = packet as ServerboundMovePlayerPacketAccessor
      val guiMoveEnabled = this.noSlow.guiMoveSetting.value
      if (!guiMoveEnabled || !this.groundSpoofPending) return
      val groundPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      if (groundPlayer.isHandsBusy) return
      if (!groundPlayer.onGround()) return
      val timer = this.airborneTimer
      if (!timer.hasElapsed(200L)) return
      if (groundPlayer.isFallFlying) return
      accessor.setOnGround(false)
      this.groundSpoofPending = false
   }

   companion object {
      private lateinit var multiTask: MultiTaskModule

      @JvmStatic
      fun bindReferencedModules(registry: ModuleRegistry) {
         multiTask = registry.getModule(
            MultiTaskModule::class.java
         ) as MultiTaskModule
      }
   }
}
