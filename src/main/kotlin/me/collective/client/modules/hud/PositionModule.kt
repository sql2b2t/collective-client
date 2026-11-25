package me.collective.client.modules.hud

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.utils.collections.IntPair
import me.collective.client.managers.TaskScheduler
import me.collective.client.utils.ElapsedTimer
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.utils.world.WorldUtils
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.player.FreecamModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.DimensionType
import me.collective.client.settings.options.HudAnchor
import me.collective.client.utils.math.MathUtils

import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.phys.Vec3

class PositionModule : HudWidgetModule("Position", "coordinates") {
   private val safeRangeSetting: Setting<Float>
   private val netherSetting: Setting<Boolean>
   private val prefixSetting: Setting<Boolean>
   private val fakeSetting: Setting<Boolean>
   private val redactionTimer = ElapsedTimer()
   private var fakeOffset: IntPair? = null

   private fun isRedactionGraceActive(): Boolean {
      val playerTicks = ClientAccess.collectiveMinecraft.player?.tickCount ?: return false

      if (playerTicks
         >= 200) {
         val timer = this.redactionTimer

         val delaySeconds = 5.0
         val delayUnit = TimeUnit.SECONDS
         if (timer.hasElapsed(delaySeconds, delayUnit)) {
            return false
         }
      }

      return true
   }

   private fun getCoordinatePrefix(): String {
      val showPrefix = this.prefixSetting.getValue()
      if (!showPrefix) {
         return ""
      }

      return "XYZ "
   }

   @EventHandler
   private fun onPlayerPositionPacket(
      event: PacketEvent.Receive
   ) {
      if (!this.isWorldUnavailable()) {
         if (event.packet is ClientboundPlayerPositionPacket) {
            val packet = event.packet as ClientboundPlayerPositionPacket
            val positionChange: PositionMoveRotation = packet.change()
            val packetPosition = positionChange.position()
            val player = ClientAccess.collectiveMinecraft.player ?: return
            val playerPosition = player.position()
            val distanceSquared = packetPosition.distanceToSqr(playerPosition)

            if (distanceSquared >= 10000.0) {
               this.redactionTimer.reset()
            }
         }
      }
   }

   init {
      val safeRangeOption = NumberSetting<Float>(
         "SafeRange", 0.0f, 0.0f, 100.0f
      )
      val safeRangeUnit = "k"
      val safeRangeWithUnit = safeRangeOption.withUnit(safeRangeUnit)
      this.safeRangeSetting = this.add(safeRangeWithUnit)

      this.netherSetting = this.add(BooleanSetting("Nether", true))

      this.prefixSetting = this.add(BooleanSetting("Prefix", true))

      this.fakeSetting = this.add(BooleanSetting("Fake", false))
      val safeRangeForDisplay = this.safeRangeSetting

      val minimumDisplayBound = SettingLabelCondition.MIN
      val minimumDisplayText = "None"
      safeRangeForDisplay.setBoundaryLabel(minimumDisplayText, minimumDisplayBound)
      val fakeCallbackOption = this.fakeSetting
      fakeCallbackOption.setChangeCallback {
         val scheduler = ClientContext.taskScheduler
         scheduler.scheduleRotationTask(Runnable {
            val negateX = MathUtils.passesRandomThreshold(50)
            val negateZ = MathUtils.passesRandomThreshold(50)
            val playerForBaseX = ClientAccess.collectiveMinecraft.player ?: return@Runnable
            val negatedPlayerX = -playerForBaseX.x

            val xBase = negatedPlayerX * 2.0
            val randomX = Math.random()
            val scaledRandomX = randomX * playerForBaseX.x

            var fakeX = (xBase + scaledRandomX * 4.0).toInt()
            val negatedPlayerZ = -playerForBaseX.z

            val zBase = negatedPlayerZ * 2.0
            val randomZ = Math.random()
            val scaledRandomZ = randomZ * playerForBaseX.z

            var fakeZ = (zBase + scaledRandomZ * 4.0).toInt()
            fakeX += 13
            fakeZ -= 37
            if (negateX) {
               fakeX = -fakeX
            }

            if (negateZ) {
               fakeZ = -fakeZ
            }

            this.fakeOffset = IntPair(fakeX, fakeZ)
         }, 0)
      }
      val coordinateRow = HudTextRow(
         Supplier<Component> coordinateVisibility@{
            if (!this.isWorldUnavailable()) {
               val safeRangeOptionForEnabled = this.safeRangeSetting
               val safeRangeEnabledValue = safeRangeOptionForEnabled.getValue()
               if (safeRangeEnabledValue > 0.0f) {
                  val positionForSafeRange = ClientAccess.collectiveMinecraft.player?.position()
                     ?: return@coordinateVisibility Component.empty()
                  val origin = Vec3.ZERO
                  val safeRangeOptionForDistance = this.safeRangeSetting
                  val safeRangeValue = safeRangeOptionForDistance.getValue()
                  val safeRangeThousands = safeRangeValue

                  val safeRangeDistance = (safeRangeThousands * 1000.0f).toDouble()
                  if (!positionForSafeRange.closerThan(origin, safeRangeDistance) &&
         this.isRedactionGraceActive()) {
                     return@coordinateVisibility Component.literal(
                        "%s%sREDACTED".format(this.getCoordinatePrefix(), ChatFormatting.WHITE.toString())
                     )
                  }
               }

               val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return@coordinateVisibility Component.empty()
               val dimension: ResourceKey<*> = level.dimension()
               val dimensionPath = dimension.location().getPath()

               val inNether = dimensionPath.contains("nether")
               val playerForDisplay = ClientAccess.collectiveMinecraft.player ?: return@coordinateVisibility Component.empty()
               var displayPosition = playerForDisplay.position()
               if (freecam.isToggled()) {
                  val camera = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera
                  val cameraPosition = camera.position
                  val playerHeight = playerForDisplay.bbHeight
                  displayPosition = cameraPosition.subtract(0.0, playerHeight.toDouble(), 0.0)
               }

               val useFakeOffset = this.fakeSetting.getValue()
               if (useFakeOffset && this.fakeOffset != null) {
                  if (this.fakeOffset!!.first() == 0) {
                     if (this.fakeOffset!!.second() == 0) {
                        this.fakeSetting.runChangeCallback()
                     }
                  }

                  displayPosition = displayPosition.add(
                     this.fakeOffset!!.first().toDouble(), 0.0, this.fakeOffset!!.second().toDouble()
                  )
               }

               val currentBlockPosition = BlockPos.containing(displayPosition)
               val convertedBlockPosition: BlockPos
               if (inNether) {
                  val convertedPosition = displayPosition.multiply(8.0, 1.0, 8.0)
                  convertedBlockPosition = BlockPos.containing(convertedPosition)
               } else {
                  val convertedPosition = displayPosition.multiply(0.125, 1.0, 0.125)
                  convertedBlockPosition = BlockPos.containing(convertedPosition)
               }

               val coordinatePrefix = this.getCoordinatePrefix()
               val prefixColor = ChatFormatting.WHITE
               val prefixColorText = prefixColor.toString()
               val formattedPrefix = coordinatePrefix + prefixColorText
               val currentX = currentBlockPosition.x
               val currentY = currentBlockPosition.y
               val currentZ = currentBlockPosition.z
               var coordinateText = currentX.toString() + ", " + currentY + ", " + currentZ
               val convertedX = convertedBlockPosition.x
               val convertedZ = convertedBlockPosition.z
               val convertedText = " (" + convertedX + ", " + convertedZ + ")"
               val showConvertedPosition = this.netherSetting.getValue()
               if (showConvertedPosition) {
                  if (WorldUtils.getDimension() !=
         DimensionType.THE_END) {
                     coordinateText += convertedText
                  }
               }

               val positionText = formattedPrefix + coordinateText
               return@coordinateVisibility Component.literal(positionText)
            }

            Component.literal("%s%sREDACTED".format(this.getCoordinatePrefix(), ChatFormatting.WHITE.toString()))
         },
         Supplier<Boolean> { true }
      )
      this.setRenderer(HudTextListRenderer(this, coordinateRow))

      this.getRenderer()
         .setAnchor(HudAnchor.BOTTOM_LEFT)
   }

   companion object {
      private val freecam: FreecamModule get() = ClientContext.getModule(FreecamModule::class.java)
   }
}
