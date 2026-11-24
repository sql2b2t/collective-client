package me.collective.client.modules.movement

import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.managers.RotationManager
import me.collective.client.utils.network.PacketSender
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.events.EventStage
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action
import net.minecraft.tags.BlockTags
import net.minecraft.world.phys.AABB

class FastClimbModule : Module("FastClimb", "Makes you climb faster.", ModuleCategory.MOVEMENT) {
   companion object {
      private var antiCheatModule: AntiCheatModule? = null

      @JvmStatic
      fun bindModules(modules: ModuleRegistry) {
         antiCheatModule = modules.getModule(AntiCheatModule::class.java) as AntiCheatModule
      }
   }

   @JvmField
   val speedSetting: Setting<Float>

   @JvmField
   val airborneSetting: Setting<Boolean>

   private var climbableWhenGrounded: Boolean = false

   init {
      this.speedSetting = this.add(NumberSetting<Float>("Speed", 0.2f, 0.0f, 1.0f))
      this.airborneSetting = this.add(BooleanSetting("Airborne", true))
      this.speedSetting.setBoundaryLabel("Ignore", SettingLabelCondition.MIN)
   }

   fun canFastClimb(): Boolean {
      if (!this.isToggled()) {
         return false
      }
      return this.airborneSetting.value || this.climbableWhenGrounded
   }

   private fun isClimbableAtPlayer(position: BlockPos): Boolean {
      return ClientAccess.collectiveMinecraft.level!!.getBlockState(position).`is`(BlockTags.CLIMBABLE) &&
         BlockPos.containing(ClientAccess.collectiveMinecraft.player!!.position()) == position
   }

   private fun updateGroundedClimbable() {
      if (!ClientAccess.collectiveMinecraft.player!!.onGround()) {
         return
      }
      var foundClimbable = false
      val bounds: AABB = ClientAccess.collectiveMinecraft.player!!.boundingBox.inflate(0.1)
      for (position in BlockPos.betweenClosed(bounds)) {
         if (this.isClimbableAtPlayer(position)) {
            foundClimbable = true
            break
         }
      }
      this.climbableWhenGrounded = foundClimbable
   }

   @EventHandler
   private fun onSendPosition(event: LocalPlayerSendPositionEvent) {
      if (EventStage.Pre == event.stage) {
         this.updateGroundedClimbable()
      }
      if (event.stage == EventStage.Post) {
         return
      }
      if (!RotationManager.isMovementSyncEnabled()) {
         return
      }
      if (!this.canFastClimb()) {
         return
      }
      val scanBounds: AABB = ClientAccess.collectiveMinecraft.player!!.boundingBox.inflate(0.1)
      BlockPos.betweenClosedStream(scanBounds)
         .map { position: BlockPos -> position.immutable() }
         .filter { position: BlockPos -> this.isClimbableAtPlayer(position) }
         .forEach { position ->
            if (!antiCheatModule!!.is2b2tEnabled()) {
               PacketSender.sendPlayerAction(Action.ABORT_DESTROY_BLOCK, position, Direction.UP)
            }
            PacketSender.sendPlayerAction(Action.STOP_DESTROY_BLOCK, position, Direction.UP)
         }
   }
}
