package me.collective.client.modules.hud


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.render.GuiDrawUtils
import me.collective.client.render.RenderUtils
import me.collective.client.render.ColorUtils
import me.collective.client.gui.ScissorStack
import me.collective.client.render.ResizableHudWidgetRenderer
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.exploit.DetectedChunk
import me.collective.client.modules.exploit.NewChunksModule
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import java.awt.Color
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.world.level.ChunkPos
import org.joml.Quaternionf

class MapModule : HudWidgetModule("Map", "minimap") {
   private val pointerModeSetting: Setting<PointerMode>
   private val widthSetting: Setting<Float>
   private val heightSetting: Setting<Float>
   private val pointerColorSetting: Setting<Color>
   private val backgroundSetting: Setting<Color>
   private val outlineSetting: Setting<Color>

   override fun calculateDimensions(): FloatArray {

      val dimensions = FloatArray(2)
      dimensions[0] = widthSetting.getValue()
      dimensions[1] = heightSetting.getValue()
      return dimensions
   }

   private fun isChunkWithinRange(chunk: ChunkPos): Boolean {
      val width = widthSetting.getValue().toDouble()
      val mapHeight = heightSetting.getValue().toDouble()
      val mapWidth = width
      val radius = Math.hypot(mapWidth, mapHeight) / 16.0
      val chunkRadius = Math.ceil(radius).toInt()
      val player = ClientAccess.collectiveMinecraft.player!!
      val playerChunk = player.chunkPosition()
      return chunk.distanceSquared(playerChunk) <= chunkRadius * chunkRadius
   }

   override fun renderContent(poses: PoseStack) {
      val widgetSize = this.renderer
         .getDimensions()
      if (widgetSize != null) {
         val renderColorValues = RenderUtils.getRenderColor()

         val previousRenderState = renderColorValues[3]

         RenderUtils.setColorAlpha(255)
         GuiDrawUtils.flush()
         RenderUtils.setColorAlpha(previousRenderState)
         val backgroundRenderer = RenderUtils.INSTANCE
         val rectangleWidth = widthSetting.getValue()
         val rectangleHeight = heightSetting.getValue()
         backgroundRenderer.fillRect(
            poses, 0.0f, 0.0f, rectangleWidth, rectangleHeight, backgroundSetting.getValue()
         )
         val outlineRenderer = RenderUtils.INSTANCE

         val outlineX = -1.0f

         val outlineY = -1.0f
         outlineRenderer.drawRectOutline(
            poses, outlineX, outlineY, widthSetting.getValue(), heightSetting.getValue(), outlineSetting.getValue()
         )
         val mapWidth = widthSetting.getValue()

         val centerX = mapWidth / 2.0f
         val mapHeight = heightSetting.getValue()

         val centerY = mapHeight / 2.0f
         poses.pushPose()
         val mapDepth = 0.0f
         poses.translate(centerX, centerY, mapDepth)

         val chunks = newChunks.getDetectedDungeons().iterator()

         while (chunks.hasNext()) {
            val dungeonPosition = chunks.next() as BlockPos
            val dungeonChunk = ChunkPos(dungeonPosition)
            if (this.isChunkWithinRange(dungeonChunk)) {
               val dungeonFill = newChunks.dungeonFillSetting.getValue()

               val dungeonColor = ColorUtils.withAlpha(
                  dungeonFill, 0.4f
               )
               this.renderChunk(poses, dungeonChunk, dungeonColor)
            }
         }

         val visibleChunks = newChunks.getVisibleChunks().iterator()

         while (visibleChunks.hasNext()) {
            val visibleChunk = visibleChunks.next() as DetectedChunk

            val chunkPosition = visibleChunk.chunkPosition()
            if (this.isChunkWithinRange(chunkPosition)) {

               val visibleKind = visibleChunk.kind()
               if (visibleKind.getColors(newChunks) != null) {

                  val visibleColoredKind = visibleChunk.kind()
                  var chunkColor = visibleColoredKind.getColors(newChunks)!![0]

                  chunkColor = ColorUtils.withAlpha(
                     chunkColor, 0.25f
                  )

                  val drawnChunkPosition = visibleChunk.chunkPosition()
                  val drawnChunkColor = chunkColor
                  this.renderChunk(poses, drawnChunkPosition, drawnChunkColor)
               }
            }
         }
         poses.popPose()
         ScissorStack.push(
            this.renderer
               .getX().toInt(),
            this.renderer
               .getY().toInt(),
            widgetSize[0].toInt(),
            widgetSize[1].toInt()
         )
         GuiDrawUtils.flush()
         ScissorStack.pop()
         this.renderPointer(poses, centerX, centerY)
      }
   }

   private fun renderChunk(
      poses: PoseStack, chunk: ChunkPos, color: Color
   ) {
      val camera = ClientAccess.collectiveMinecraft.gameRenderer.getMainCamera()
      val cameraPosition = camera.position()
      val cameraOffsetX = (cameraPosition.x - chunk.minBlockX).toFloat()

      val scaledOffsetX = cameraOffsetX / 2.0f

      val offsetX = scaledOffsetX - 8.0f
      val cameraOffsetZ = (cameraPosition.z - chunk.minBlockZ).toFloat()

      val scaledOffsetZ = cameraOffsetZ / 2.0f

      val offsetZ = scaledOffsetZ - 8.0f
      poses.pushPose()
      val markerDepth = 0.0f
      poses.translate(offsetX, offsetZ, markerDepth)

      val markerWidth = 8.0f

      GuiDrawUtils.fillRect(
         poses, 0.0f, 0.0f, markerWidth, 8.0f, color
      )
      poses.popPose()
   }

   private fun renderPointer(poses: PoseStack, centerX: Float, centerY: Float) {
      poses.pushPose()
      val pointerDepth = 0.0f
      poses.translate(centerX, centerY, pointerDepth)
      if (pointerModeSetting.getValue() == MapModule.PointerMode.DOT) {
         val dotRenderer = RenderUtils.INSTANCE

         val dotX = -1.0f

         val dotY = -1.0f

         val dotWidth = 1.0f

         val dotHeight = 1.0f
         dotRenderer.fillRect(
            poses, dotX, dotY, dotWidth, dotHeight, pointerColorSetting.getValue()
         )
      } else {
         if (pointerModeSetting.getValue() == MapModule.PointerMode.ARROW) {

            val arrowScaleX = 0.05f

            val arrowScaleY = 0.05f

            val arrowScaleZ = 1.0f
            poses.scale(arrowScaleX, arrowScaleY, arrowScaleZ)
            val rotationAxis = Axis.ZP

            val rotationOffset = 45.0f
            val camera = ClientAccess.collectiveMinecraft.gameRenderer.getMainCamera()
            val rotationDegrees = rotationOffset + camera.yRot()
            val arrowRotation: Quaternionf = rotationAxis.rotationDegrees(rotationDegrees)
            poses.mulPose(arrowRotation)
            val renderStateValues = RenderUtils.getRenderColor()

            val renderAlpha = renderStateValues[3].toFloat()
            val pointerArgb = ColorUtils.toArgb(
               pointerColorSetting.getValue(), renderAlpha
             )
            val texture = navigationTexture

            GuiDrawUtils.drawTexture(
               poses,
               texture,
               -128,
               -128,
               0.0f,
               0.0f,
               256,
               256,
               256,
               256,
               pointerArgb
            )
            val bufferSource = ClientAccess.collectiveMinecraft.renderBuffers().bufferSource()
            bufferSource.endBatch()
         }
      }
      poses.popPose()
   }

   init {
      this.pointerModeSetting = this.add(EnumSetting("Pointer", MapModule.PointerMode.ARROW))

      this.widthSetting = this.add(NumberSetting(
         "Width",
         76.0f,
         50.0f,
         500.0f
      ))
      this.heightSetting = this.add(NumberSetting(
         "Height",
         76.0f,
         50.0f,
         250.0f
      ))
      this.pointerColorSetting = this.add(ColorSetting("Pointer", Color.white))
      this.backgroundSetting = this.add(ColorSetting(
         "Background",
         Color(
            10,
            10,
            10,
            50
         )
      ))
      this.outlineSetting = this.add(ColorSetting(
         "Outline",
         Color(
            10,
            10,
            10,
            100
         )
      ))
      this.pointerColorSetting.setConfigName("PointerColor")
      this.pointerColorSetting.forceOpaqueColor()
      val descriptionColor = ChatFormatting.YELLOW
      val descriptionPrefix = descriptionColor.toString()
      val descriptionText = descriptionPrefix + "Primarily used for NewChunks"

      this.setDescription(descriptionText)
      val widget = ResizableHudWidgetRenderer(this)
      widget.enableResizing(this)
      this.setRenderer(widget)
   }

   private enum class PointerMode(private val displayName: String) : NamedEntry {
      NONE("None"),
      ARROW("Arrow"),
      DOT("Dot");

      override fun getName(): String {
         return this.displayName
      }
   }

   companion object {
      private val newChunks: NewChunksModule get() = ClientContext.getModule(NewChunksModule::class.java)

      private val navigationTexture: Identifier

      init {
         val texturePath = "textures/nav.png"
         val textureNamespace = "collective"
         navigationTexture = Identifier.fromNamespaceAndPath(textureNamespace, texturePath)
      }
   }
}
