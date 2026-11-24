package me.collective.client.modules.movement


import me.collective.client.ClientAccess
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.render.FontRenderer
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import java.util.function.Predicate
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.Direction.Axis
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.player.Abilities
import net.minecraft.world.entity.player.Abilities.Packed
import net.minecraft.world.entity.player.Input

class FlightModule : Module("Flight", "Allows you to fly using magic.", ModuleCategory.MOVEMENT) {
   private val modeSetting: Setting<ModeChoice>
   private val speedSetting: Setting<Float>
   private val verticalSetting: Setting<Float>
   private val glideSetting: Setting<Float>
   private val antiKickSetting: Setting<Boolean>
   val accelerateSetting: Setting<Boolean>
   val startSpeedSetting: Setting<Float>
   val timeSetting: Setting<Float>
   private var savedAbilities: Packed? = null
   private var accelerationStartTimeMillis: Long = 0

   protected fun computeFlightSpeed(): Double {
      if (this.accelerateSetting.value && this.modeSetting.value != ModeChoice.JETPACK) {
         return MovementPhysics.accelerateTo(
            this.speedSetting.value.toDouble(), this.startSpeedSetting.value.toDouble(),
            this.timeSetting.value.toDouble(), this.accelerationStartTimeMillis
         )
      }
      return this.speedSetting.value.toDouble()
   }

   private fun saveAbilities() {
      // Мир может отсутствовать при загрузке конфига (Module.fromJson → setToggled).
      val player = ClientAccess.collectiveMinecraft.player ?: return
      this.savedAbilities = player.getAbilities().pack()
   }

   private fun restoreAbilities() {
      val restored: Packed? = this.savedAbilities
      val player = ClientAccess.collectiveMinecraft.player
      if (restored != null && player != null) {
         player.getAbilities().apply(restored)
         this.savedAbilities = null
      }
   }

   override fun getInfo(): String? {
      return try {
         FontRenderer.capitalize(this.modeSetting.value)
      } catch (failure: Exception) {
         null
      }
   }

   override fun onEnable() {
      if (this.modeSetting.value == FlightModule.ModeChoice.VANILLA) {
         this.saveAbilities()
      }

      this.accelerationStartTimeMillis = System.currentTimeMillis()
   }

   override fun onDisable() {
      if (!this.isWorldUnavailable()) {
         if (this.modeSetting.value == FlightModule.ModeChoice.VANILLA) {
            this.restoreAbilities()
         }
      }
   }

   @EventHandler
   private fun onLocalPlayerSendPositionEvent(event: LocalPlayerSendPositionEvent) {
      val mode: ModeChoice = this.modeSetting.value
      mode.handleSendPosition(this, event)
   }

   @EventHandler
   private fun onLocalPlayerMoveEvent(event: LocalPlayerMoveEvent) {
      if (!MovementPhysics.isLocalPlayerMoving()) {
         this.accelerationStartTimeMillis = System.currentTimeMillis()
      }

      val mode: ModeChoice = this.modeSetting.value
      mode.handleMovement(this, event)
      if (this.antiKickSetting.value) {
         if (ClientAccess.collectiveMinecraft.player!!.onGround()) {
            return
         }
         var verticalOffset: Double = 0.04
         val playerTicks: Int = ClientAccess.collectiveMinecraft.player!!.tickCount

         if (playerTicks % 2 == 0) {
            verticalOffset = -verticalOffset
         }

         val eventY: Double = event.y + verticalOffset
         event.setY(eventY)
         ClientAccess.collectiveMinecraft.player!!.setDeltaMovement(
            ClientAccess.collectiveMinecraft.player!!.getDeltaMovement().add(0.0, verticalOffset, 0.0)
         )
      }
   }

   @EventHandler
   private fun onPacketReceive(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundPlayerPositionPacket) {
         this.accelerationStartTimeMillis = System.currentTimeMillis()
      }
   }

   init {
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", ModeChoice.VANILLA))
      this.speedSetting = this.add(NumberSetting<Float>("Speed", 1.0f, 0.1f, 10.0f))
      this.verticalSetting = this.add(NumberSetting<Float>("Vertical", 1.0f, 0.0f, 5.0f))
      this.glideSetting = this.add(NumberSetting<Float>("Glide", 0.0f, 0.0f, 5.0f))
      this.antiKickSetting = this.add(BooleanSetting("AntiKick", false))
      this.accelerateSetting = this.add(BooleanSetting("Accelerate", false).markVisibilityDependency())
      this.startSpeedSetting = this.add(NumberSetting<Float>("StartSpeed", 1.0f, 0.0f, 5.0f).dependsOn(this.accelerateSetting))
      this.timeSetting = this.add(NumberSetting<Float>("Time", 1.0f, 0.1f, 10.0f).withUnit("s").dependsOn(this.accelerateSetting))
      this.verticalSetting.addVisibilityPredicate(Predicate<Float> { ignored -> this.modeSetting.value == ModeChoice.STATIC })
      this.glideSetting.addVisibilityPredicate(Predicate<Float> { ignored -> this.modeSetting.value == ModeChoice.STATIC })
      this.antiKickSetting.addVisibilityPredicate(Predicate<Boolean> { ignored -> this.modeSetting.value == ModeChoice.STATIC })
      this.accelerateSetting.addVisibilityPredicate(Predicate<Boolean> { ignored -> this.modeSetting.value != ModeChoice.JETPACK })
      this.startSpeedSetting.addVisibilityPredicate(Predicate<Float> { ignored -> this.modeSetting.value != ModeChoice.JETPACK })
      this.timeSetting.addVisibilityPredicate(Predicate<Float> { ignored -> this.modeSetting.value != ModeChoice.JETPACK })
      this.antiKickSetting.addVisibilityPredicate(Predicate<Boolean> { ignored -> this.modeSetting.value != ModeChoice.JETPACK })
      this.speedSetting.disableNumericBounds()
      this.verticalSetting.disableNumericBounds()
      this.modeSetting.setChangeCallback {
         if (this.isToggled()) {
            if (this.modeSetting.value == ModeChoice.VANILLA) {
               this.saveAbilities()
            } else {
               this.restoreAbilities()
            }
         }
      }
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      VANILLA("Vanilla") {
         override fun handleMovement(
            module: FlightModule, event: LocalPlayerMoveEvent
         ) {
            ClientAccess.collectiveMinecraft.player!!.getAbilities().flying = true
            val abilities: Abilities = ClientAccess.collectiveMinecraft.player!!.getAbilities()
            val flyingSpeed: Float = (module.computeFlightSpeed() * 0.05f.toDouble()).toFloat()
            abilities.setFlyingSpeed(flyingSpeed)
         }
      },
      STATIC("Static") {
         private var jumpTicks: Int = 0

         override fun handleMovement(module: FlightModule, event: LocalPlayerMoveEvent) {
            var verticalSpeed: Double = module.verticalSetting.value.toDouble()
            val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            if (player.input.keyPresses.jump()) {
               this.jumpTicks++
               if (module.antiKickSetting.value) {
                  if (this.jumpTicks >= 17) {
                     verticalSpeed = -0.15
                  }
                  if (this.jumpTicks >= 21) {
                     this.jumpTicks = 0
                  }
               }
               event.setY(verticalSpeed)
               player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, verticalSpeed))
            } else if (player.input.keyPresses.shift()) {
               event.setY(-verticalSpeed)
               player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, -verticalSpeed))
            } else {
               val idleY: Float = (if (player.onGround()) player.getDeltaMovement().y else 0.0).toFloat()
               event.setY(idleY.toDouble())
               player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, idleY.toDouble()))
               if (!player.verticalCollision && module.glideSetting.value != 0.0f) {
                  player.setDeltaMovement(player.getDeltaMovement().add(0.0, -module.glideSetting.value.toDouble(), 0.0))
                  event.setY(player.getDeltaMovement().y)
               }
            }
            val horizontal: DoubleArray = MovementPhysics.applyHorizontalMotion(event, module.computeFlightSpeed())
            player.setDeltaMovement(horizontal[0], player.getDeltaMovement().y, horizontal[1])
         }
      },
      JETPACK("JetPack") {
         override fun handleSendPosition(
            module: FlightModule, event: LocalPlayerSendPositionEvent
         ) {
            val jumpInput: Input = ClientAccess.collectiveMinecraft.player!!.input.keyPresses
            if (jumpInput.jump()) {
               ClientAccess.collectiveMinecraft.player!!.setDeltaMovement(ClientAccess.collectiveMinecraft.player!!.getDeltaMovement().add(0.0, module.computeFlightSpeed() * 0.1, 0.0))
            }
         }
      },
      SOURCE("Source") {
         override fun handleMovement(
            module: FlightModule, event: LocalPlayerMoveEvent
         ) {
            val speed: Double = module.computeFlightSpeed()
            MovementPhysics.applyHorizontalMotion(event, speed)

            if (MovementPhysics.isLocalPlayerMoving()) {
               val forwardAmount: Float = ClientAccess.collectiveMinecraft.player!!.input.moveVector.y
               val forwardDirection: Double = (-Math.signum(forwardAmount)).toDouble()
               val pitchDegrees: Double = ClientAccess.collectiveMinecraft.player!!.getXRot().toDouble()
               val pitchRadians: Double = Math.toRadians(pitchDegrees)
               val verticalVelocity: Double = forwardDirection * Math.sin(pitchRadians) * speed
               event.setY(verticalVelocity)
            } else {
               event.setY(0.0)
            }
         }
      };

      override fun getName(): String {
         return this.displayName
      }

      open fun handleSendPosition(
         module: FlightModule, event: LocalPlayerSendPositionEvent
      ) {
      }

      open fun handleMovement(
         module: FlightModule, event: LocalPlayerMoveEvent
      ) {
      }
   }
}
