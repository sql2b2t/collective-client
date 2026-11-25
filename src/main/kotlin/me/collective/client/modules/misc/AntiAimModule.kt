package me.collective.client.modules.misc


import me.collective.client.utils.math.MathConstants
import java.util.function.Predicate
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.events.RotationRequestEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.managers.RotationManager
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.combat.RaycastUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import java.util.Iterator
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ProjectileWeaponItem
import net.minecraft.world.item.TridentItem
import net.minecraft.world.phys.Vec3

class AntiAimModule : Module("AntiAim", "Sets your rotations server-side.", ModuleCategory.MISC) {
   private val yawEnabledSetting: Setting<Boolean>
   private val yawModeSetting: Setting<YawChoice>
   internal val spinSpeedSetting: Setting<Float>
   internal val staticYawSetting: Setting<Float>
   internal val relativeYawSetting: Setting<Float>
   internal val flipSetting: Setting<Float>
   internal val jitterRangeSetting: Setting<Float>
   internal val baseSetting: Setting<Float>
   private val pitchEnabledSetting: Setting<Boolean>
   private val pitchModeSetting: Setting<PitchChoice>
   internal val staticPitchSetting: Setting<Float>
   internal val relativePitchSetting: Setting<Float>
   @JvmField
   val endermanSetting: Setting<Boolean>
   private val endermanLookSetting: Setting<LookChoice>
   private var rotationApplied: Boolean
   internal var spinYaw: Float
   private var pauseTicks: Int

   private fun findVisibleEnderman(): EnderMan? {
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return null
      val entities: MutableIterator<Entity> = level.entitiesForRendering().iterator()
      while (entities.hasNext()) {
         val entity = entities.next()
         if (entity !is EnderMan) {
            continue
         }
         if (entity.isCreepy || !entity.isAlive) {
            continue
         }
         val lookSetting = this.endermanLookSetting
         if (lookSetting.value == LookChoice.AVOID &&
         !this.isLookingAtEnderman(entity)) {
            continue
         }

         if (this.endermanLookSetting.value == LookChoice.STARE) {
            val endermanEyePosition = entity.eyePosition
            val playerEyePosition = ClientAccess.collectiveMinecraft.player?.eyePosition ?: continue
            val distance = endermanEyePosition.distanceTo(playerEyePosition)
            if (distance > 48.0) {
               continue
            }
         }

         if (RaycastUtils.hasVisibleEntitySample(entity)) {
            return entity
         }
      }
      return null
   }

   fun pauseRotations() {

      this.pauseTicks = 2
   }

   @EventHandler(priority = -100)
   private fun onRotationEvent(event: RotationRequestEvent) {
      this.rotationApplied = false
      var rotationManagerSnapshot: RotationManager = ClientContext.rotationManager
      if (rotationManagerSnapshot.request != null) {
         return
      }
      if (this.pauseTicks > 0) {
         this.pauseTicks--
         return
      }
      var yaw = event.playerYaw
      var pitch = event.playerPitch
      val yawEnabledSnapshot = this.yawEnabledSetting
      val yawEnabled = yawEnabledSnapshot.value
      if (yawEnabled) {
         val yawModeSnapshot = this.yawModeSetting
         val yawMode = yawModeSnapshot.value
         val yawPlayer = ClientAccess.collectiveMinecraft.player ?: return
         val playerYaw = yawPlayer.yRot
         yaw = yawMode.getYaw(this, playerYaw)

         this.rotationApplied = true
      }

      val pitchEnabledSnapshot = this.pitchEnabledSetting
      val pitchEnabled = pitchEnabledSnapshot.value
      if (pitchEnabled) {
         val pitchModeSnapshot = this.pitchModeSetting
         val pitchMode = pitchModeSnapshot.value
         val pitchPlayer = ClientAccess.collectiveMinecraft.player ?: return
         val playerPitch = pitchPlayer.xRot
         pitch = pitchMode.getPitch(this, playerPitch)

         this.rotationApplied = true
      }

      val endermanEnabledSnapshot = this.endermanSetting
      val endermanEnabled = endermanEnabledSnapshot.value
      if (endermanEnabled) {

         val enderman = this.findVisibleEnderman()
         if (enderman != null) {
            val avoidModeSnapshot = this.endermanLookSetting
            if (avoidModeSnapshot.value == LookChoice.AVOID) {

               pitch = 90.0f

               this.rotationApplied = true
            }

            val stareModeSnapshot = this.endermanLookSetting
            if (stareModeSnapshot.value == LookChoice.STARE) {
               val endermanEyePosition = enderman.eyePosition

               val stareAngles = RotationManager.getRotationsToPosition(
                  endermanEyePosition
               )
               yaw = stareAngles[0]
               pitch = stareAngles[1]

               this.rotationApplied = true
            }
         }
      }

      if (this.rotationApplied) {
         rotationManagerSnapshot = ClientContext.rotationManager

         val rotationAngles = FloatArray(2)
         rotationAngles[0] = yaw
         rotationAngles[1] = pitch

         rotationManagerSnapshot.requestRotation(rotationAngles, -999, true)
      }
   }

   private fun isLookingAtEnderman(enderman: EnderMan): Boolean {
      val viewPlayer = ClientAccess.collectiveMinecraft.player ?: return false

      val viewVector = viewPlayer.getViewVector(1.0f)
      val lookDirection = viewVector.normalize()
      var endermanDirection = Vec3(
         enderman.x - viewPlayer.x,
         enderman.eyeY - viewPlayer.eyeY,
         enderman.z - viewPlayer.z
      )
      val distance = endermanDirection.length()
      endermanDirection = endermanDirection.normalize()
      val dotProduct = lookDirection.dot(endermanDirection)

      return dotProduct > (1.0) - 0.025 / distance
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      val mainHandPlayer = ClientAccess.collectiveMinecraft.player ?: return
      val mainHandStack = mainHandPlayer.mainHandItem
      val mainHandItem = mainHandStack.item
      val projectileItem = mainHandItem is ProjectileWeaponItem || mainHandItem is TridentItem
      if (projectileItem) {
         val usingPlayer = ClientAccess.collectiveMinecraft.player ?: return
         if (usingPlayer.isUsingItem) {

            this.pauseTicks = 2
         }
      }
   }

   @EventHandler
   private fun onConnectionSendPacketEvent(
      event: PacketEvent.Send
   ) {
      if (event.packet is ServerboundUseItemPacket) {
         val useItemPacket = event.packet as ServerboundUseItemPacket
         val useItemHand = useItemPacket.hand
         this.pauseForItemUse(useItemHand)
      }

      if (event.packet is ServerboundUseItemOnPacket) {
         val useOnPacket = event.packet as ServerboundUseItemOnPacket
         val useOnHand = useOnPacket.hand
         this.pauseForItemUse(useOnHand)
      }
   }

   private fun pauseForItemUse(hand: InteractionHand) {
      if (ClientAccess.collectiveMinecraft.player == null) {
         return
      }
      val player = ClientAccess.collectiveMinecraft.player ?: return
      val stack = player.getItemInHand(hand)
      if (stack.isEmpty) {
         return
      }
      val item = stack.item
      if (item !is BlockItem && !BlockInteractionUtils.isThrowableItem(item)) {
         return
      }

      this.pauseTicks = 2
   }

   init {
      this.yawEnabledSetting = this.add(BooleanSetting("Yaw", false).markVisibilityDependency())
      this.yawModeSetting = this.add(EnumSetting<YawChoice>("Yaw", YawChoice.JITTER).dependsOn(this.yawEnabledSetting))
      val showSpinSpeed = Predicate<Any> { ignored -> this.yawModeSetting.value == YawChoice.SPIN }
      this.spinSpeedSetting = this.add(NumberSetting<Float>("Speed", 1.0f, 0.1f, 90.0f, showSpinSpeed).withUnit("°").dependsOn(this.yawEnabledSetting))
      this.staticYawSetting = this.add(NumberSetting<Float>("Static", 0.0f, 0.0f, 360.0f,
         Predicate<Any> { ignored -> this.yawModeSetting.value == YawChoice.STATIC }).withUnit("°").dependsOn(this.yawEnabledSetting))
      this.relativeYawSetting = this.add(NumberSetting<Float>("Relative", 0.0f, -180.0f, 180.0f,
         Predicate<Any> { ignored -> this.yawModeSetting.value == YawChoice.RELATIVE }).withUnit("°").dependsOn(this.yawEnabledSetting))
      this.flipSetting = this.add(NumberSetting<Float>("Flip", 0.0f, 0.0f, 90.0f,
         Predicate<Any> { ignored -> this.yawModeSetting.value == YawChoice.FLIP }).withUnit("°").dependsOn(this.yawEnabledSetting))
      this.jitterRangeSetting = this.add(NumberSetting<Float>("JitterRange", 0.0f, 0.0f, 180.0f,
         Predicate<Any> { ignored -> this.yawModeSetting.value == YawChoice.JITTER }).withUnit("°").dependsOn(this.yawEnabledSetting))
      this.baseSetting = this.add(NumberSetting<Float>("Base", 0.0f, 0.0f, 360.0f,
         Predicate<Any> { ignored -> this.yawModeSetting.value == YawChoice.FLIP ||
         this.yawModeSetting.value == YawChoice.JITTER }).withUnit("°").dependsOn(this.yawEnabledSetting))
      this.pitchEnabledSetting = this.add(BooleanSetting("Pitch", false).markVisibilityDependency())
      this.pitchModeSetting = this.add(EnumSetting<PitchChoice>("Pitch", PitchChoice.STATIC).dependsOn(this.pitchEnabledSetting))
      this.staticPitchSetting = this.add(NumberSetting<Float>("Static", 0.0f, -90.0f, 90.0f,
         Predicate<Any> { ignored -> this.pitchModeSetting.value == PitchChoice.STATIC }).withUnit("°").dependsOn(this.pitchEnabledSetting))
      this.relativePitchSetting = this.add(NumberSetting<Float>("Relative", 0.0f, -90.0f, 90.0f,
         Predicate<Any> { ignored -> this.pitchModeSetting.value == PitchChoice.RELATIVE }).withUnit("°").dependsOn(this.pitchEnabledSetting))
      this.endermanSetting = this.add(BooleanSetting("Enderman", false).markVisibilityDependency())
      this.endermanLookSetting = this.add(EnumSetting<LookChoice>("Look", LookChoice.AVOID).dependsOn(this.endermanSetting))
      this.staticYawSetting.setConfigName("StaticYaw")
      this.staticPitchSetting.setConfigName("StaticPitch")
      this.relativeYawSetting.setConfigName("RelativeYaw")
      this.relativePitchSetting.setConfigName("RelativePitch")
      this.yawModeSetting.setConfigName("YawMode")
      this.pitchModeSetting.setConfigName("PitchMode")
      this.rotationApplied = false
      this.spinYaw = 0.0f
      this.pauseTicks = 0
   }

   enum class LookChoice(private val displayName: String) : NamedEntry {
      AVOID("Avoid"),
      STARE("Stare");

      override fun getName(): String {
         return this.displayName
      }
   }

   enum class PitchChoice(private val displayName: String) : NamedEntry {
      STATIC("Static") {
         override fun getPitch(module: AntiAimModule, playerPitch: Float): Float {
            return module.staticPitchSetting.value
         }
      },
      RELATIVE("Relative") {
         override fun getPitch(module: AntiAimModule, playerPitch: Float): Float {
            val pitch = module.relativePitchSetting.value + playerPitch
            val minimumPitch = (-MathConstants.NINETY_DEGREES).toFloat()
            val maximumPitch = MathConstants.NINETY_DEGREES.toFloat()
            return Mth.clamp(pitch, minimumPitch, maximumPitch)
         }
      },
      RANDOM("Random") {
         override fun getPitch(module: AntiAimModule, playerPitch: Float): Float {
            return (
               Math.random() * 180.0 - MathConstants.NINETY_DEGREES
               ).toFloat()
         }
      };

      override fun getName(): String {
         return this.displayName
      }

      abstract fun getPitch(module: AntiAimModule, playerPitch: Float): Float
   }

   enum class YawChoice(private val displayName: String) : NamedEntry {
      JITTER("Jitter") {
         override fun getYaw(module: AntiAimModule, playerYaw: Float): Float {
            var jitterSample = Math.random()
            jitterSample = jitterSample * module.jitterRangeSetting.value * 2.0
            jitterSample -= module.jitterRangeSetting.value
            return Mth.wrapDegrees(jitterSample + module.baseSetting.value).toFloat()
         }
      },
      SPIN("Spin") {
         override fun getYaw(module: AntiAimModule, playerYaw: Float): Float {
            val previousSpinYaw = module.spinYaw
            module.spinYaw = previousSpinYaw + module.spinSpeedSetting.value
            val yawToWrap = module.spinYaw
            return Mth.wrapDegrees(yawToWrap)
         }
      },
      FLIP("Flip") {
         override fun getYaw(module: AntiAimModule, playerYaw: Float): Float {
            val baseYaw = module.baseSetting.value
            val flipOffset: Float
            if ((ClientAccess.collectiveMinecraft.player?.tickCount ?: 0) % 2 ==
         0
            ) {
               flipOffset = -module.flipSetting.value
            } else {
               flipOffset = module.flipSetting.value
            }

            return Mth.wrapDegrees(baseYaw + flipOffset)
         }
      },
      STATIC("Static") {
         override fun getYaw(module: AntiAimModule, playerYaw: Float): Float {
            return module.staticYawSetting.value
         }
      },
      RELATIVE("Relative") {
         override fun getYaw(module: AntiAimModule, playerYaw: Float): Float {
            return module.relativeYawSetting.value + playerYaw
         }
      },
      RANDOM("Random") {
         override fun getYaw(module: AntiAimModule, playerYaw: Float): Float {
            return (
               Math.random() * MathConstants.FULL_TURN_DEGREES
               ).toFloat()
         }
      };

      override fun getName(): String {
         return this.displayName
      }

      abstract fun getYaw(module: AntiAimModule, playerYaw: Float): Float
   }
}
