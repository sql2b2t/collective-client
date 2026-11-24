package me.collective.client.modules.render


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.BlockOutlineRenderEvent
import me.collective.client.render.WorldBoxRenderer
import me.collective.client.render.VertexGeometry
import me.collective.client.render.MeshBuffer
import me.collective.client.render.ClientRenderTypes
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.exploit.ReachModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexFormat
import java.awt.Color
import net.minecraft.core.BlockPos
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CarpetBlock
import net.minecraft.world.level.block.CoralFanBlock
import net.minecraft.world.level.block.GrowingPlantBlock
import net.minecraft.world.level.block.StandingSignBlock
import net.minecraft.world.level.block.VegetationBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes

class HighlightModule : Module {
   private val modeSetting: Setting<HighlightModule.HighlightMode>
   private val depthSetting: Setting<Boolean>
   private val unreachableSetting: Setting<Boolean>
   private val lineWidthSetting: Setting<Float>
   private val colorSetting: Setting<Color>
   private val fillColorSetting: Setting<Color>

   private fun getHighlightHitResult(): BlockHitResult? {
      val reachModule = reach
      if (reachModule!!.isToggled()) {
         val ghostHandSetting = reach!!.ghostHandSetting
         val ghostHandEnabled = ghostHandSetting.value
         if (ghostHandEnabled) {
            val ghostHandReceiver = reach

            val ghostHandHit = ghostHandReceiver!!.getGhostHandHitResult()
            if (ghostHandHit != null) {
               if (ghostHandHit.type != HitResult.Type.MISS) {
                  if (ghostHandHit.blockPos != null) {
                     val ghostHandReceiverForReturn = reach

                     return ghostHandReceiverForReturn!!.getGhostHandHitResult()
                  }
               }
            }
         }
      }

      if (!this.isAirPlaceHighlight()) {
         val crosshairHit = ClientAccess.collectiveMinecraft.hitResult
         if (crosshairHit is BlockHitResult) {
            if (crosshairHit.type != HitResult.Type.MISS) {
               return crosshairHit
            }
         }

         return null
      } else {

         return ReachModule.raycastForPlacement() as? BlockHitResult
      }
   }

   private fun isAirPlaceHighlight(): Boolean {
      val reachModule = reach
      if (!reachModule!!.isToggled()) {
         return false
      }
      val airPlaceSetting = reach!!.airPlaceSetting
      val airPlaceEnabled = airPlaceSetting.value
      if (!airPlaceEnabled) {
         return false
      }
      var heldItem = ClientAccess.collectiveMinecraft.player!!.mainHandItem.item
      if (this.isAirPlaceBlockItem(heldItem)) {
         return true
      }
      heldItem = ClientAccess.collectiveMinecraft.player!!.offhandItem.item
      return this.isAirPlaceBlockItem(heldItem)
   }

   private fun shouldKeepVanillaOutline(): Boolean {
      return (this.unreachableSetting.value) && this.unreachableSetting.isVisible()
   }

   private fun isAirPlaceBlockItem(item: Item): Boolean {
      if (item !is BlockItem) {
         return false
      }
      val block = item.block
      return !(block is VegetationBlock || block is GrowingPlantBlock ||
         block is CarpetBlock || block is CoralFanBlock || block is StandingSignBlock) &&
         !EXCLUDED_AIR_PLACE_BLOCKS.contains(block)
   }

   @EventHandler
   private fun onBlockOutlineRender(event: BlockOutlineRenderEvent) {

      if (!this.shouldKeepVanillaOutline()) {
         event.cancel()
      }
   }

   private fun renderHighlightBoxes(
      poseStack: PoseStack, renderer: HighlightModule.BoxRenderer
   ) {
      if (this.isWorldUnavailable()) {
         return
      }

      val hitResult = this.getHighlightHitResult()
      if (hitResult == null) {
         return
      }
      val blockPos = hitResult.blockPos
      val level = ClientAccess.collectiveMinecraft.level!!
      val blockState = level!!.getBlockState(blockPos)

      if (!this.isAirPlaceHighlight()) {
         if (blockState.block is AirBlock) {
            return
         }
      }

      val shapeLevel = ClientAccess.collectiveMinecraft.level!!
      val shape = blockState.getShape(shapeLevel!!, blockPos)
      if (shape.isEmpty()) {

         if (this.isAirPlaceHighlight()) {
            val fullBlockShape = Shapes.block()
            val airPlaceBox = fullBlockShape.bounds()
            renderer.submit(poseStack, airPlaceBox, blockPos)
         }
         return
      }

      if (this.shouldKeepVanillaOutline()) {
         return
      }
      if (this.modeSetting.value == HighlightModule.HighlightMode.COMPLEX) {
         val shapeBoxes = shape.toAabbs()
         for (box in shapeBoxes) {
            renderer.submit(poseStack, box, blockPos)
         }
      } else {
         if (this.modeSetting.value == HighlightModule.HighlightMode.SIDE) {
            var sideBox = shape.bounds()
            val face = hitResult.direction
            val boxSize = Vec3(sideBox.xsize, sideBox.ysize, sideBox.zsize)
            val halfExtents = Vec3(
               if (face.stepX == 0) 0.5 else 0.0,
               if (face.stepY == 0) 0.5 else 0.0,
               if (face.stepZ == 0) 0.5 else 0.0
            )
            val faceExtents = halfExtents.multiply(boxSize)
            val centerBox = AABB(sideBox.center, sideBox.center)
            val faceOffset = ((face.unitVec3).multiply(boxSize)).scale(0.5)
            val movedFaceBox = centerBox.move(faceOffset)
            val extentX = faceExtents.x
            val extentY = faceExtents.y
            val extentZ = faceExtents.z
            sideBox = movedFaceBox.inflate(extentX, extentY, extentZ)
            renderer.submit(poseStack, sideBox, blockPos)
         } else {
            val simpleBox = shape.bounds()
            renderer.submit(poseStack, simpleBox, blockPos)
         }
      }
   }

   @EventHandler
   private fun onWorldRender(event: WorldRenderEvent.Main) {
      val depthEnabled = this.depthSetting.value
      if (!depthEnabled) {
         val highlightPoseStack = event.poseStack!!
         val renderer = BoxRenderer { poseStack, box, blockPos ->
            val worldBox = box.move(blockPos)
            WorldBoxRenderer.fillBox(
               poseStack, worldBox, this.fillColorSetting.value
            )
            val outlineWorldBox = box.move(blockPos)
            val outlineColor = this.colorSetting.value
            val lineWidth = this.lineWidthSetting.value
            WorldBoxRenderer.outlineBox(
               poseStack, outlineWorldBox, outlineColor, lineWidth
            )
         }
         this.renderHighlightBoxes(highlightPoseStack, renderer)
      }
   }

   @EventHandler
   private fun onBeforeBufferSource(event: WorldRenderEvent.BeforeBufferSource) {
      val depthEnabled = this.depthSetting.value
      if (depthEnabled) {
         FILL_BUFFER.begin(
            VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR
         )
         OUTLINE_BUFFER.begin(
            VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH
         )
         val highlightPoseStack = event.poseStack!!
         val renderer = BoxRenderer { poseStack, box, blockPos ->

            var inflation = 0.002F
            if (this.modeSetting.value == HighlightModule.HighlightMode.SIDE) {

               inflation = -0.0003F
            } else {
               val emptyBlockLevel = ClientAccess.collectiveMinecraft.level!!
               if (emptyBlockLevel!!.isEmptyBlock(blockPos)) {

                  inflation = -0.005F
               }
            }

            val worldBox = box.move(blockPos)
            val inflationAmount = inflation.toDouble()
            val expandedBox = worldBox.inflate(inflationAmount)
            val fillBuffer = FILL_BUFFER
            val fillPose = poseStack.last()
            val poseMatrix = fillPose.pose()
            val fillColor = this.fillColorSetting.value
            VertexGeometry.appendFilledBox(
               fillBuffer, poseMatrix, expandedBox, fillColor.hashCode()
            )
            val outlineBuffer = OUTLINE_BUFFER
            val outlinePose = poseStack.last()
            val outlineColor = this.colorSetting.value
            VertexGeometry.appendBoxOutline(
               outlineBuffer, outlinePose, expandedBox, this.lineWidthSetting.value, outlineColor.hashCode()
            )
         }
         this.renderHighlightBoxes(highlightPoseStack, renderer)
         FILL_BUFFER.draw(
            ClientRenderTypes.worldFillDepth()
         )
         OUTLINE_BUFFER.draw(
            ClientRenderTypes.worldLinesDepth()
         )

      }
   }

   init {
      this.modeSetting = this.add(EnumSetting<HighlightMode>("Mode", HighlightMode.SIMPLE))
      this.depthSetting = this.add(BooleanSetting("Depth", false))
      this.unreachableSetting = this.add(BooleanSetting("Unreachable", false))
      this.lineWidthSetting = this.add(NumberSetting<Float>("LineWidth", 1.0F, 0.1F, 3.0F))
      this.colorSetting = this.add(ColorSetting("Color", Color(1992137215, true)))
      this.fillColorSetting = this.add(ColorSetting("FillColor", Color(12425727, true)))
      this.setDrawn(false)
      this.unreachableSetting.addVisibilityPredicate { ignoredValue ->
         val reachModule = reach
         var visible = false
         if (reachModule!!.isToggled()) {
            val liquidPlaceSetting = reach!!.liquidPlaceSetting
            val liquidPlaceEnabled = liquidPlaceSetting.value
            if (liquidPlaceEnabled) {
               visible = true
            } else {
               val airPlaceSetting = reach!!.airPlaceSetting
               val airPlaceEnabled = airPlaceSetting.value
               if (airPlaceEnabled) {
                  visible = true
               }
            }
         }
         visible
      }
   }

   private enum class HighlightMode(private val displayName: String) : NamedEntry {
      SIMPLE("Simple"),
      SIDE("Side"),
      COMPLEX("Complex");

      override fun getName(): String {
         return this.displayName
      }

   }

   private fun interface BoxRenderer {
      fun submit(poseStack: PoseStack, box: AABB, blockPos: BlockPos)
   }

   companion object {
      private val EXCLUDED_AIR_PLACE_BLOCKS: Set<Block>
      private val FILL_BUFFER: MeshBuffer
      private val OUTLINE_BUFFER: MeshBuffer
      private var reach: ReachModule?

      init {

         val cactus = Blocks.CACTUS
         val chorusFlower = Blocks.CHORUS_FLOWER
         val snow = Blocks.SNOW
         val mossCarpet = Blocks.MOSS_CARPET
         val pointedDripstone = Blocks.POINTED_DRIPSTONE
         val bamboo = Blocks.BAMBOO
         EXCLUDED_AIR_PLACE_BLOCKS = setOf(cactus, chorusFlower, snow, mossCarpet, pointedDripstone, bamboo)

         FILL_BUFFER = MeshBuffer.create(
            8192
         )

         OUTLINE_BUFFER = MeshBuffer.create(
            8192
         )
         reach = ClientContext.getModule(ReachModule::class.java)
      }

      @JvmStatic
      fun bindModuleDependencies(registry: ModuleRegistry) {
         reach = registry.getModule(ReachModule::class.java) as ReachModule?
      }
   }

   constructor() : super("Highlight", "Highlights the block you're looking at.", ModuleCategory.RENDER, "blockhighlight")
}
