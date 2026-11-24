package me.collective.client.modules.movement


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ClientPacketListenerHandleExplosionEvent
import me.collective.client.events.LivingEntityRotationEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LivingEntityJumpFromGroundEvent
import me.collective.client.events.LocalPlayerAiStepEvent
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerMoveTowardsClosestSpaceEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.utils.movement.StrafeSpeedHandler
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.movement.GrimSpeedHandler
import me.collective.client.events.StepHeightEvent
import me.collective.client.utils.combat.DamageUtils
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.movement.StrictStrafeSpeedHandler
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.events.EventStage
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import java.util.function.Predicate
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class SpeedModule : Module("Speed", "Makes you move faster.", ModuleCategory.MOVEMENT) {
   companion object {
      private var obstaclePasserModule: ObstaclePasserModule? = null

      @JvmStatic
      fun bindReferencedModules(registry: ModuleRegistry) {
         obstaclePasserModule = registry.getModule(ObstaclePasserModule::class.java) as? ObstaclePasserModule
         StrafeSpeedHandler.bindModules(registry)
         StrictStrafeSpeedHandler.bindModules(registry)
         GrimSpeedHandler.bindModules(registry)
      }
   }

   @JvmField
   val modeSetting: Setting<ModeChoice>

   @JvmField
   val speedSetting: Setting<Float>

   @JvmField
   val boostSetting: Setting<Boolean>

   @JvmField
   val inLiquidSetting: Setting<Boolean>

   @JvmField
   val useTimerSetting: Setting<Boolean>

   @JvmField
   val pauseInBlocksSetting: Setting<Boolean>

   private var skipNextMoveAfterStep: Boolean = false

   @JvmField
   val explosionBoostTimer: ElapsedTimer

   private val modeHandlers: EnumSelectionMap<SpeedModeHandler>

   fun reset() {
      val handler: SpeedModeHandler = this.modeHandlers.selected
      handler.lastHorizontalDistance = 0.0
      handler.moveSpeed = 0.0
      handler.movementStage = if (this.modeSetting.value == ModeChoice.OnGround) 2 else 4
   }

   override fun getInfo(): String? {
      val selectedMode: ModeChoice = this.modeSetting.value
      return selectedMode.getName()
   }

   override fun onEnable() {
      this.modeHandlers.selected.onEnable()
   }

   @EventHandler(priority = 100)
   private fun onLocalPlayerSendPositionEvent(event: LocalPlayerSendPositionEvent) {
      if (ClientContext.serverStateManager.teleportAckTimer.hasElapsed(10L)) {
         this.modeHandlers.selected.onSendPosition(event)
      }
   }

   @EventHandler
   private fun onLocalPlayerAiStepEvent(event: LocalPlayerAiStepEvent) {
      if (this.modeSetting.value == ModeChoice.StrafeStrict && !this.shouldPauseSpeed()) {
         ClientAccess.collectiveMinecraft.player!!.setSprinting(true)
      }
   }

   @EventHandler
   private fun onStepHeight(event: StepHeightEvent) {
      if (EventStage.Post == event.stage && ClientAccess.collectiveMinecraft.player!!.onGround() && event.height > 0.6) {
         this.skipNextMoveAfterStep = true
      }
   }

   @EventHandler
   private fun onLocalPlayerMoveTowardsClosestSpaceEvent(event: LocalPlayerMoveTowardsClosestSpaceEvent) {
      if (this.modeSetting.value == ModeChoice.Grim) {
         event.cancel()
      }
   }

   @EventHandler
   private fun onClientPacketListenerHandleExplosionEvent(event: ClientPacketListenerHandleExplosionEvent) {
      val explosionPosition: Vec3 = Vec3(event.centerX, event.centerY, event.centerZ)
      val explosionDamage: Float = DamageUtils.getCrystalDamage(explosionPosition, ClientAccess.collectiveMinecraft.player!!)

      if (!MovementPhysics.isLocalPlayerMoving()) {
         return
      }
      if (!ClientContext.serverStateManager.setbackTimer.hasElapsed(500L)) {
         return
      }
      if (!(this.boostSetting.value)) {
         return
      }
      val explosionDistance: Double = Math.sqrt(ClientAccess.collectiveMinecraft.player!!.distanceToSqr(explosionPosition))
      if (explosionDistance > 6.0 || explosionDamage < 4.0f) {
         return
      }
      this.explosionBoostTimer.reset()
   }

   @EventHandler
   private fun onLivingEntityJumpFromGroundEvent(event: LivingEntityJumpFromGroundEvent) {
      if (this.modeSetting.value == ModeChoice.StrafeStrict &&
         MovementPhysics.isLocalPlayerMoving() && !this.shouldPauseSpeed()) {
         event.cancel()
      }
   }

   @EventHandler
   private fun onLocalPlayerMoveEvent(event: LocalPlayerMoveEvent) {
      if (!ClientContext.serverStateManager.teleportAckTimer.hasElapsed(10L)) {
         return
      }
      if (this.skipNextMoveAfterStep) {
         this.skipNextMoveAfterStep = false
      } else {
         this.modeHandlers.selected.onMove(event)
      }
   }

   @EventHandler
   private fun onRotationEvent(event: LivingEntityRotationEvent) {
      if (!event.isJump) {
         this.modeHandlers.selected.onRotationUpdate()
      }
   }

   fun shouldYieldToObstaclePasser(): Boolean {
      return obstaclePasserModule?.isPassingObstacles() == true
   }

   fun shouldPauseInBlocks(): Boolean {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return false
      val level = ClientAccess.collectiveMinecraft.level ?: return false
      val playerBounds: AABB = player.boundingBox
      val position: BlockPos = player.blockPosition()
      val column: AABB = AABB(position.x.toDouble(), playerBounds.minY, position.z.toDouble(),
         position.x + 1.0, playerBounds.maxY, position.z + 1.0).deflate(1e-7)
      return level.collidesWithSuffocatingBlock(player, column) &&
         this.pauseInBlocksSetting.value
   }

   fun shouldPauseInLiquid(): Boolean {
      val player = ClientAccess.collectiveMinecraft.player ?: return false
      return PlayerStateUtils.isInLiquid(player) && !this.inLiquidSetting.value
   }

   @EventHandler
   private fun onPacketReceive(event: PacketEvent.Receive) {
      if (event.packet is ClientboundPlayerPositionPacket) {
         this.reset()
      }
   }

   fun shouldPauseSpeed(): Boolean {
      return this.isWorldUnavailable() || this.shouldPauseInBlocks() || this.shouldPauseInLiquid() ||
         PlayerStateUtils.isHungerLimited() || ClientAccess.collectiveMinecraft.player!!.isFallFlying() ||
         ClientAccess.collectiveMinecraft.player!!.isSpectator() || ClientContext.baritoneBridge.isPathingOrCalculating()
   }

   init {
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", ModeChoice.Strafe))
      val speedVisibility: Predicate<Any> = Predicate { ignored -> this.modeSetting.value != ModeChoice.StrafeStrict &&
         this.modeSetting.value != ModeChoice.Grim }
      this.speedSetting = this.add(NumberSetting<Float>("Speed", 1.0f, 0.0f, 10.0f, speedVisibility))
      val boostVisibility: Predicate<Any> = Predicate { ignored -> this.modeSetting.value == ModeChoice.StrafeStrict ||
         this.modeSetting.value == ModeChoice.Strafe }
      this.boostSetting = this.add(BooleanSetting("Boost", false, boostVisibility))
      val liquidVisibility: Predicate<Any> = Predicate { ignored -> this.modeSetting.value != ModeChoice.Grim }
      this.inLiquidSetting = this.add(BooleanSetting("InLiquid", false, liquidVisibility))
      val timerVisibility: Predicate<Any> = Predicate { ignored -> this.modeSetting.value != ModeChoice.Grim }
      this.useTimerSetting = this.add(BooleanSetting("UseTimer", true, timerVisibility))
      val blocksVisibility: Predicate<Any> = Predicate { ignored -> this.modeSetting.value != ModeChoice.Grim }
      this.pauseInBlocksSetting = this.add(BooleanSetting("PauseInBlocks", true, blocksVisibility))
      this.explosionBoostTimer = ElapsedTimer()
      this.modeHandlers = EnumSelectionMap<SpeedModeHandler>(this.modeSetting)
      this.modeHandlers.register(ModeChoice.Strafe, StrafeSpeedHandler(this))
      this.modeHandlers.register(ModeChoice.StrafeStrict, StrictStrafeSpeedHandler(this))
      this.modeHandlers.register(ModeChoice.Vanilla, VanillaSpeedHandler(this))
      this.modeHandlers.register(ModeChoice.OnGround, OnGroundSpeedHandler(this))
      this.modeHandlers.register(ModeChoice.Grim, GrimSpeedHandler(this))
   }

   enum class ModeChoice : NamedEntry {
      Strafe,
      StrafeStrict,
      Vanilla,
      OnGround,
      Grim;

      override fun getName(): String {
         return this.name
      }
   }
}
