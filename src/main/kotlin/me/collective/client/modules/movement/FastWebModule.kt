package me.collective.client.modules.movement


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerAiStepEvent
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.events.EventStage
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import java.util.function.Predicate
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.Axis
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action
import net.minecraft.world.entity.player.Input
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class FastWebModule : Module("FastWeb", "Makes you faster in cobwebs.", ModuleCategory.MOVEMENT) {
   companion object {
      private var antiCheatModule: AntiCheatModule? = null

      @JvmStatic
      fun bindModules(registry: ModuleRegistry) {
         antiCheatModule = registry.getModule(AntiCheatModule::class.java) as AntiCheatModule
      }

      private fun currentXDisplacement(): Double {
         return ClientAccess.collectiveMinecraft.player!!.x - ClientAccess.collectiveMinecraft.player!!.xo
      }
   }

   private val modeSetting: Setting<ModeChoice>
   private val horizontalSetting: Setting<Boolean>
   private val speedSetting: Setting<Float>
   private val verticalSetting: Setting<Boolean>
   private val vSpeedSetting: Setting<Float>
   private val upwardsSetting: Setting<Boolean>
   private var usingStrictTimer: Boolean

   init {
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", ModeChoice.PLAIN))
      val showMovement: Predicate<Any> = Predicate { ignored -> this.modeSetting.value != ModeChoice.GRIM }
      this.horizontalSetting = this.add(BooleanSetting("Horizontal", false, showMovement).markVisibilityDependency())
      this.speedSetting = this.add(NumberSetting<Float>("Speed", 1.0f, 1.0f, 2.0f, showMovement).dependsOn(this.horizontalSetting))
      this.verticalSetting = this.add(BooleanSetting("Vertical", false, showMovement).markVisibilityDependency())
      this.vSpeedSetting = this.add(NumberSetting<Float>("VSpeed", 1.0f, 1.0f, 2.0f, showMovement).dependsOn(this.verticalSetting))
      val showUpwards: Predicate<Any> = Predicate { ignored -> this.modeSetting.value == ModeChoice.PLAIN }
      this.upwardsSetting = this.add(BooleanSetting("Upwards", false, showUpwards).dependsOn(this.verticalSetting))
      this.usingStrictTimer = false
      this.setDrawn(false)
   }

   fun reset() {
      ClientContext.playerTickController.releaseMultiplier(this)
      this.usingStrictTimer = false
   }

   override fun onDisable() {
      // Без сброса usingStrictTimer «залипал» и TimerModule видел его всю сессию.
      this.reset()
   }

   fun isUsingStrictTimer(): Boolean {
      return this.usingStrictTimer
   }

   private fun isStrictMode(): Boolean {
      return this.modeSetting.value == ModeChoice.STRICT
   }

   fun isGrimEnabled(): Boolean {
      if (!this.isToggled()) {
         return false
      }
      return this.modeSetting.value == ModeChoice.GRIM
   }

   @EventHandler(priority = -100)
   private fun onAiStep(event: LocalPlayerAiStepEvent) {
      if (!this.isGrimEnabled()) {
         return
      }
      if (!ClientAccess.collectiveMinecraft.player!!.input.keyPresses.jump()) {
         return
      }
      if (PlayerStateUtils.isInCobweb(ClientAccess.collectiveMinecraft.player!!)) {
         ClientAccess.collectiveMinecraft.player!!.setSprinting(false)
      }
   }

   @EventHandler
   private fun onMove(event: LocalPlayerMoveEvent) {
      if (!ClientContext.serverStateManager.setbackTimer.hasElapsed(500L)) {
         this.reset()
         return
      }
      if (!PlayerStateUtils.isInCobweb(ClientAccess.collectiveMinecraft.player!!)) {
         this.reset()
         return
      }
      if (this.modeSetting.value == ModeChoice.GRIM) {
         this.reset()
         return
      }
      var horizontalMultiplier: Float = this.speedSetting.value
      if (this.verticalSetting.value) {
         horizontalMultiplier = this.applyVerticalDescent(event, horizontalMultiplier)
         if (this.upwardsSetting.value) {
            val jumpKeys: Input = ClientAccess.collectiveMinecraft.player!!
               .input
               .keyPresses
            if (jumpKeys.jump()) {
               if (!this.isStrictMode()) {
                  val ascentPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
                  val ascentVelocity: Vec3 = ascentPlayer.deltaMovement
                  val ascentAxis: Axis = Axis.Y
                  val upwardVelocity: Double = this.vSpeedSetting.value.toDouble()
                  val updatedAscentVelocity: Vec3 = ascentVelocity.with(ascentAxis, upwardVelocity)
                  ascentPlayer.setDeltaMovement(updatedAscentVelocity)
                  val upwardEventY: Double = this.vSpeedSetting.value.toDouble()
                  event.setY(upwardEventY)
               }
            }
         }
      }
      if (MovementPhysics.isLocalPlayerMoving()) {
         if (this.horizontalSetting.value) {
            val includeEffects: Boolean = true
            val horizontalSpeed: Double = MovementPhysics.getBaseMoveSpeed(
                  includeEffects
               ) * horizontalMultiplier
            val horizontalEvent: LocalPlayerMoveEvent = event
            val horizontalMotion: DoubleArray = MovementPhysics.applyHorizontalMotion(
               horizontalEvent, horizontalSpeed
            )
            val horizontalReceiverSnapshot: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val horizontalXSnapshot: Double = horizontalMotion[0]
            val yVelocityPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val horizontalZ: Double = horizontalMotion[1]
            val preservedY: Double = yVelocityPlayer.deltaMovement.y
            horizontalReceiverSnapshot.setDeltaMovement(horizontalXSnapshot, preservedY, horizontalZ)
         }
      }
   }

   private fun applyVerticalDescent(event: LocalPlayerMoveEvent, horizontalMultiplier: Float): Float {
      if (!ClientAccess.collectiveMinecraft.player!!.input.keyPresses.shift() || ClientAccess.collectiveMinecraft.player!!.onGround()) {
         this.reset()
         return horizontalMultiplier
      }
      if (this.isStrictMode()) {
         this.usingStrictTimer = true
         ClientContext.playerTickController.requestMultiplier(this, this.vSpeedSetting.value * 6.0f)
         return horizontalMultiplier
      }
      this.reset()
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      player.setDeltaMovement(player.deltaMovement.with(Axis.Y, -this.vSpeedSetting.value.toDouble()))
      event.setY(-this.vSpeedSetting.value.toDouble())
      return Math.min(horizontalMultiplier, 1.2f)
   }

   @EventHandler
   private fun onSendPosition(event: LocalPlayerSendPositionEvent) {
      if (EventStage.Pre == event.stage) {
         val displacement: Vec3 = Vec3(
            currentXDisplacement(),
            ClientAccess.collectiveMinecraft.player!!.y - ClientAccess.collectiveMinecraft.player!!.yo,
            ClientAccess.collectiveMinecraft.player!!.z - ClientAccess.collectiveMinecraft.player!!.zo
         )
         if (this.isGrimEnabled()) {
            if (displacement.lengthSqr() > 0.0) {
               val bounds: AABB = ClientAccess.collectiveMinecraft.player!!.boundingBox
               BlockPos.betweenClosedStream(bounds)
                  .map { webPos -> webPos.immutable() }
                  .filter { webPos -> ClientAccess.collectiveMinecraft.level!!.getBlockState(webPos).`is`(Blocks.COBWEB) }
                  .forEach { webPos ->
                     if (!antiCheatModule!!.is2b2tEnabled()) {
                        PacketSender.sendPlayerAction(Action.ABORT_DESTROY_BLOCK, webPos, Direction.UP)
                     }
                     PacketSender.sendPlayerAction(Action.STOP_DESTROY_BLOCK, webPos, Direction.UP)
                  }
            }
         }
         if (this.modeSetting.value == ModeChoice.GRIM) {
            if (ClientAccess.collectiveMinecraft.player!!.input.keyPresses.shift()) {
               if (!ClientAccess.collectiveMinecraft.player!!.onGround()) {
                  if (PlayerStateUtils.isInCobweb(ClientAccess.collectiveMinecraft.player!!)) {
                     val playerY: Double = ClientAccess.collectiveMinecraft.player!!.y
                     val randomizedY: Double = playerY + Math.random()
                     val spoofedY: Double = randomizedY + 1.0
                     event.y = spoofedY
                  }
               }
            }
         }
      }
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      PLAIN("Plain"),
      STRICT("Strict"),
      LAGBACK("LagBack"),
      GRIM("Grim");

      override fun getName(): String {
         return this.displayName
      }
   }
}
