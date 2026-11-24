package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.render.WorldBoxRenderer
import me.collective.client.render.RenderUtils
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.AABB

class BordersModule : Module("Borders", "Shows region borders on your screen.", ModuleCategory.RENDER) {
   private val chunkSetting: Setting<Color>
   private val mapSetting: Setting<Color>
   private val lineWidthSetting: Setting<Float>
   private val mapSizeSetting: Setting<Int>
   private val levelSetting: Setting<Int>

   @EventHandler
   private fun onWorldRender(event: WorldRenderEvent.Main) {
      val chunkColor: Color = this.chunkSetting.getValue()!!
      if (chunkColor.getAlpha() != 0) {

         val chunkRegion: Region = this.createChunkRegion()
         val chunkPoseStack: PoseStack = event.poseStack!!
         val chunkDrawColor: Color = this.chunkSetting.getValue()!!
         val chunkLevel: Int = this.levelSetting.getValue()!!
         val chunkDrawLineWidth: Float = this.lineWidthSetting.getValue()!!
         chunkRegion.render(chunkPoseStack, chunkDrawColor, chunkLevel, chunkDrawLineWidth)
      }

      val mapColor: Color = this.mapSetting.getValue()!!
      if (mapColor.getAlpha() != 0) {

         val mapRegion: Region = this.createMapRegion()
         val mapPoseStack: PoseStack = event.poseStack!!
         val mapDrawColor: Color = this.mapSetting.getValue()!!
         val mapDrawLevel: Int = this.levelSetting.getValue()!!
         val mapDrawLineWidth: Float = this.lineWidthSetting.getValue()!!
         mapRegion.render(mapPoseStack, mapDrawColor, mapDrawLevel, mapDrawLineWidth)
      }
   }

   init {
      this.chunkSetting = this.add(ColorSetting("Chunk", Color(-65536, true)))
      this.mapSetting = this.add(ColorSetting("Map", Color(-65281, true)))
      this.lineWidthSetting = this.add(NumberSetting<Float>("LineWidth", 1.0F, 0.1F, 3.0F))
      this.mapSizeSetting = this.add(NumberSetting<Int>("MapSize", 0, 0, 4))
      this.levelSetting = this.add(NumberSetting<Int>("Level", -1, -1, 320))
      val minimumBoundary: SettingLabelCondition = SettingLabelCondition.MIN
      this.levelSetting.setBoundaryLabel("~", minimumBoundary)
   }

   private fun createChunkRegion(): Region {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val chunk: ChunkPos = player.chunkPosition()
      return Region(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(), getChunkRegionSize())
   }

   private fun createMapRegion(): Region {
      val mapSize: Int = 128 * (1 shl this.mapSizeSetting.getValue()!!)
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val mapColumn: Int = Mth.floor((player.getBlockX() + 64.0) / mapSize)
      val mapRow: Int = Mth.floor((player.getBlockZ() + 64.0) / mapSize)
      val centerX: Int = mapColumn * mapSize + mapSize / 2 - 64
      val centerZ: Int = mapRow * mapSize + mapSize / 2 - 64
      return Region(centerX, centerZ, mapSize)
   }

   private data class Region(
      val centerX: Int,
      val centerZ: Int,
      val size: Int
   ) {

      fun render(
         poseStack: PoseStack, color: Color, level: Int, lineWidth: Float
      ) {
         var renderY: Double = level.toDouble()
         if (level == -1) {
            val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val partialTick: Float = RenderUtils.getPartialTick()
            renderY = player.getPosition(partialTick).y
         }

         val bounds: AABB = this.getBounds(renderY)
         WorldBoxRenderer.outlineBox(poseStack, bounds, color, lineWidth)
      }

      fun getBounds(level: Double): AABB {
         val regionSize: Int = this.size

         val halfSize: Int = regionSize / 2
         return AABB(
            (this.centerX - halfSize).toDouble(),
            level,
            (this.centerZ - halfSize).toDouble(),
            (this.centerX + halfSize).toDouble(),
            level,
            (this.centerZ + halfSize).toDouble()
         )
      }

   }

   companion object {
      private fun getChunkRegionSize(): Int {
         return 16
      }
   }
}
