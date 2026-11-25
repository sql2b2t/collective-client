package me.collective.client.modules.movement

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.VehicleMoveEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.render.RenderUtils
import me.collective.client.utils.KeyUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import java.util.function.Predicate
import java.util.function.Supplier
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.Saddleable
import net.minecraft.world.entity.animal.horse.AbstractHorse
import net.minecraft.world.entity.vehicle.Boat
import net.minecraft.world.phys.Vec3

class EntityControlModule : Module("EntityControl", "Helps with riding entities.", ModuleCategory.MOVEMENT) {
   private val copyYawSetting: Setting<Boolean>
   @JvmField
   val horseJumpSetting: Setting<Boolean>
   private val grimOverflowSetting: Setting<Boolean>
   private val entitySpeedSetting: Setting<Boolean>
   private val speedSetting: Setting<Double>
   private val noCollisionSetting: Setting<Boolean>
   private val pathFinderSetting: Setting<Boolean>
   private val glideSetting: Setting<Boolean>
   private val accelerateSetting: Setting<Boolean>
   private val flightSetting: Setting<Boolean>
   private val verticalSetting: Setting<Float>
   private var useNegativeFlightJitter: Boolean = false
   private var accelerationStartMillis: Long = 0
   @Volatile
   private var grimOverflowActive: Boolean = false
   private val pathfinderMotion: Supplier<Vec3?>?

   init {
      this.copyYawSetting = this.add(BooleanSetting("CopyYaw", false))
      this.horseJumpSetting = this.add(BooleanSetting("HorseJump", true))
      this.grimOverflowSetting = this.add(BooleanSetting("GrimOverflow", false))
      this.entitySpeedSetting = this.add(BooleanSetting("EntitySpeed", true).markVisibilityDependency())
      val speedVisibility: Predicate<Any> = Predicate { ignored -> this.entitySpeedSetting.isExpanded }
      this.speedSetting = this.add(NumberSetting<Double>("Speed", 1.0, 0.1, 20.0, speedVisibility))
      val noCollisionVisibility: Predicate<Any> = Predicate { ignored -> this.entitySpeedSetting.isExpanded }
      this.noCollisionSetting = this.add(BooleanSetting("NoCollision", true, noCollisionVisibility))
      this.pathFinderSetting = this.add(BooleanSetting("Baritone", false).dependsOn(this.entitySpeedSetting))
      this.pathFinderSetting.hideWithoutBaritone()
      val glideVisibility: Predicate<Any> = Predicate { ignored -> this.entitySpeedSetting.isExpanded }
      this.glideSetting = this.add(BooleanSetting("Glide", false, glideVisibility))
      val accelerateVisibility: Predicate<Any> = Predicate { ignored -> this.entitySpeedSetting.isExpanded }
      this.accelerateSetting = this.add(BooleanSetting("Accelerate", false, accelerateVisibility))
      val flightVisibility: Predicate<Any> = Predicate { ignored -> this.entitySpeedSetting.isExpanded }
      this.flightSetting = this.add(BooleanSetting("Flight", false, flightVisibility).markVisibilityDependency())
      this.verticalSetting = this.add(NumberSetting<Float>("Vertical", 10.0f, 0.0f, 20.0f).dependsOn(this.flightSetting, this.entitySpeedSetting))
      this.speedSetting.setChangeCallback { this.accelerationStartMillis = System.currentTimeMillis() }
      this.pathfinderMotion = if (ClientContext.baritoneBridge.isAvailable()) {
         // Один экземпляр на модуль: конструктор регистрирует процесс и слушателя в Baritone,
         // а создание нового pathfinder'а на каждый запрос давало утечку и всегда null-результат.
         val entityPathfinder = EntityControlPathfinder(this)
         Supplier { entityPathfinder.getMotion() }
      } else null
   }

   override fun onDisable() {
      this.grimOverflowActive = false
      if (!this.isWorldUnavailable() && ClientAccess.collectiveMinecraft.player!!.vehicle is Boat) {
         (ClientAccess.collectiveMinecraft.player!!.vehicle as Boat).setNoGravity(false)
      }
   }

   @EventHandler
   private fun onVehicleMoveEvent(event: VehicleMoveEvent) {
      if (this.isWorldUnavailable()) {
         return
      }
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val vehicle: Entity? = player.vehicle
      if (vehicle == null) {
         return
      }
      if (!MovementPhysics.isLocalPlayerMoving()) {
         this.accelerationStartMillis = System.currentTimeMillis()
      }
      if (this.copyYawSetting.value) {
         vehicle.yRot = player.getViewYRot(RenderUtils.getPartialTick())
      }
      if (vehicle is Saddleable && !vehicle.isSaddled) {
         return
      }
      var speed: Double = this.speedSetting.value
      val verticalSpeed: Double = this.verticalSetting.value.toDouble()
      if (speed >= 20.0) {
         speed = 19.99
      }
      if (this.accelerateSetting.value) {
         speed = MovementPhysics.accelerateTo(
            this.speedSetting.value, 0.1, this.speedSetting.value, this.accelerationStartMillis
         )
      }
      val pathMotion: Vec3? = this.pathfinderMotion?.get()
      if (!this.entitySpeedSetting.value || pathMotion == null && this.shouldPauseVehicleSpeed()) {
         return
      }
      val horizontalVelocity: DoubleArray = if (pathMotion == null)
         MovementPhysics.getHorizontalMotion(player.getViewYRot(RenderUtils.getPartialTick()), player.input, speed)
      else
         doubleArrayOf(pathMotion.x, pathMotion.z)
      var velocityY: Double
      if (this.flightSetting.value) {
         if (vehicle is Boat) {
            vehicle.setNoGravity(true)
         }
         var descend: Boolean = KeyUtils.isPhysicallyDown(ClientAccess.collectiveMinecraft.options.keySprint)
         if (ClientAccess.collectiveMinecraft.screen is ChatScreen || ClientAccess.collectiveMinecraft.screen is ClientScreen) {
            descend = false
         }
         velocityY = if (pathMotion != null) {
            if (Math.abs(pathMotion.y) > 0.04125f.toDouble()) {
               pathMotion.y
            } else if (this.useNegativeFlightJitter) {
               (-0.04125f).toDouble()
            } else {
               0.04125f.toDouble()
            }
         } else if (ClientAccess.collectiveMinecraft.options.keyJump.isDown) {
            verticalSpeed
         } else if (this.glideSetting.value || descend) {
            -verticalSpeed
         } else {
            if (this.useNegativeFlightJitter) {
               (-0.04125f).toDouble()
            } else {
               0.04125f.toDouble()
            }
         }
      } else {
         velocityY = event.y
      }
      event.setVelocity(Vec3(horizontalVelocity[0], velocityY, horizontalVelocity[1]))
      this.useNegativeFlightJitter = !this.useNegativeFlightJitter
   }

   fun isEntityPathfindingEnabled(): Boolean {
      return this.isToggled() && this.pathFinderSetting.value && this.entitySpeedSetting.value && this.flightSetting.value
   }

   fun getEntityPathSpeed(): Double {
      return Math.min(this.speedSetting.value, 19.99)
   }

   fun getEntityPathVerticalSpeed(): Double {
      return this.verticalSetting.value.toDouble()
   }

   private fun shouldPauseVehicleSpeed(): Boolean {
      val vehicle: Entity? = ClientAccess.collectiveMinecraft.player!!.vehicle
      return vehicle != null && vehicle.horizontalCollision && this.noCollisionSetting.value
   }

   @EventHandler
   private fun updateGrimOverflow(event: LocalPlayerPreTickEvent) {
      val vehicle: Entity? = ClientAccess.collectiveMinecraft.player!!.vehicle
      this.grimOverflowActive = this.grimOverflowSetting.value &&
         vehicle is AbstractHorse && this.isControlledByLocalPlayer(vehicle)
   }

   private fun isControlledByLocalPlayer(entity: Entity?): Boolean {
      if (entity == null) {
         return false
      }
      return entity.controllingPassenger === ClientAccess.collectiveMinecraft.player!!
   }

   @EventHandler
   private fun resetGrimOverflow(event: MinecraftDisconnectEvent) {
      this.grimOverflowActive = false
   }

   fun isGrimOverflowActive(): Boolean {
      return this.grimOverflowActive
   }

   @EventHandler
   fun onPacketReceive(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundPlayerPositionPacket) {
         this.accelerationStartMillis = System.currentTimeMillis()
      }
   }
}
