package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.events.PacketEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.render.WorldBoxRenderer
import me.collective.client.managers.RotationManager
import me.collective.client.render.ColorUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundLoginPacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.util.Mth
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB

class VoidESPModule : Module("VoidESP", "Highlights void blocks.", ModuleCategory.RENDER) {
   private val lineWidthSetting: Setting<Float> = add(NumberSetting("LineWidth", 1.0F, 0.1F, 3.0F))

   private val heightSetting: Setting<Int> = add(NumberSetting("Height", 0, -1, 4).withUnit("m"))

   private val radiusSetting: Setting<Int> = add(NumberSetting("Radius", 10, 1, 16).withUnit("m"))

   internal val fadeSetting: Setting<Boolean> = add(BooleanSetting("Fade", true))

   private val fadeRadiusSetting: Setting<Float> = add(NumberSetting("FadeRadius", 7.0F, 1.0F, 16.0F) { ignored -> this.fadeSetting.value!! }.withUnit("m"))

   internal val colorsSetting: Setting<Boolean> = add(BooleanSetting("Colors", true).markAsGroup().markVisibilityDependency())

   private val fillSetting: Setting<Color> = add(ColorSetting("Fill", Color(822018048, true)) { ignored -> this.colorsSetting.isExpanded() })

   private val lineSetting: Setting<Color> = add(ColorSetting("Line", Color(-1140916224, true)) { ignored -> this.colorsSetting.isExpanded() })

   private val voidBlocks: MutableSet<BlockPos> = HashSet()

   private var resetPending: Boolean = false

   private fun shouldRemoveVoidBlock(position: BlockPos): Boolean {
      val gameRenderer = ClientAccess.collectiveMinecraft.gameRenderer
      val cameraPosition = gameRenderer.mainCamera.position()
      val radiusValue = this.radiusSetting.value!!
      if (!position.closerToCenterThan(cameraPosition, radiusValue.toDouble())) {
         return true
      }

      return this.isBedrock(position)
   }

   private fun isVoidBlock(position: BlockPos): Boolean {
      return position.y == ClientAccess.collectiveMinecraft.level!!.minY && !this.isBedrock(position)
   }

   private fun isBedrock(position: BlockPos): Boolean {
      return WorldUtils.getBlock(position) === Blocks.BEDROCK
   }

   private fun getColumnHeight(position: BlockPos): Int {
      var columnHeight = this.heightSetting.value!!
      if (columnHeight <= 1) {
         return columnHeight
      }

      var offset = 0
      while (offset <= columnHeight) {
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         val columnPosition = position.above(offset)
         val blockState = level.getBlockState(columnPosition)
         if (!blockState.canBeReplaced()) {
            columnHeight -= columnHeight - offset
         }
         offset++
      }

      return maxOf(columnHeight, 1)
   }

   override fun getInfo(): String? {
      val blockCount = this.voidBlocks.size
      return blockCount.toString()
   }

   override fun onDisable() {
      this.voidBlocks.clear()
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      if (this.resetPending) {
         this.voidBlocks.clear()
         this.resetPending = false
      }

      this.voidBlocks.removeIf { position -> this.shouldRemoveVoidBlock(position) }
      val gameRenderer = ClientAccess.collectiveMinecraft.gameRenderer
      val cameraPosition = gameRenderer.mainCamera.position()
      val scanRadius: Float = this.radiusSetting.value!!.toFloat()
      WorldUtils.streamSphereBlocks(cameraPosition, scanRadius)
         .filter { position -> this.isVoidBlock(position) }
         .forEach { position -> this.voidBlocks.add(position) }
   }

   @EventHandler
   private fun onWorldRender(event: WorldRenderEvent.Main) {
      val positions = this.voidBlocks.iterator()

      while (positions.hasNext()) {
         val position = positions.next()
         val blockBox = AABB(position)
         val baseY = position.y

         val maxY = (baseY + this.getColumnHeight(position)).toDouble()
         val box = blockBox.setMaxY(maxY)

         if (RotationManager.isVisible(box)) {
            val gameRenderer = ClientAccess.collectiveMinecraft.gameRenderer
            val cameraPosition = gameRenderer.mainCamera.position()
            var fillColor = this.fillSetting.value!!
            var lineColor = this.lineSetting.value!!
            val blockCenter = position.center
            val distance = cameraPosition.distanceTo(blockCenter)
            if (this.fadeSetting.value!!) {
               val fadeRadiusValue = this.fadeRadiusSetting.value!!
               if (distance >= fadeRadiusValue) {

                  val distancePastFadeStart = distance - this.fadeRadiusSetting.value!!
                  val renderRadius: Float = this.radiusSetting.value!!.toFloat()
                  val fadeRangeStartValue = this.fadeRadiusSetting.value!!
                  val fadeProgress = distancePastFadeStart / (renderRadius - fadeRangeStartValue)

                  val alphaFactor = 1.0F - Mth.clamp(fadeProgress, 0.0, 1.0).toFloat()
                  fillColor = ColorUtils.withAlpha(
                     fillColor, (alphaFactor * fillColor.alpha).toInt()
                  )
                  lineColor = ColorUtils.withAlpha(
                     lineColor, (alphaFactor * lineColor.alpha).toInt()
                  )
               }
            }

            WorldBoxRenderer.fillBox(
               event.poseStack!!, box, fillColor
            )
            val poseStack: PoseStack = event.poseStack!!
            WorldBoxRenderer.outlineBox(poseStack, box, lineColor, this.lineWidthSetting.value!!)
         }
      }
   }

   @EventHandler
   private fun onPacketReceive(event: PacketEvent.Receive) {
      if (event.packet !is ClientboundRespawnPacket) {
         if (event.packet !is ClientboundLoginPacket) {
            return
         }
      }

      this.resetPending = true
   }

}
