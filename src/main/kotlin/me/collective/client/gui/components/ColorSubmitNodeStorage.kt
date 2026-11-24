package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.Font.DisplayMode
import net.minecraft.client.model.Model
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.SubmitNodeCollection
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeStorage
import net.minecraft.client.renderer.block.MovingBlockRenderState
import net.minecraft.client.renderer.block.model.BlockStateModel
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

class ColorSubmitNodeStorage : SubmitNodeStorage() {
   var colorArgb: Int = 0
      @JvmName("getColorArgbProp") get
      @JvmName("setColorArgbProp") set

   fun setColorArgb(colorArgb: Int) { this.colorArgb = colorArgb }
   fun getColorArgb(): Int = this.colorArgb

   private var itemTintColors: IntArray? = null

   override fun order(order: Int): SubmitNodeCollection {
      return this.submitsPerOrder.computeIfAbsent(order) { ColoredSubmitNodeCollection(this) }
   }

   private inner class ColoredSubmitNodeCollection(storage: SubmitNodeStorage) : SubmitNodeCollection(storage) {
      override fun submitShadow(poseStack: PoseStack, shadowRadius: Float, shadowPieces: List<EntityRenderState.ShadowPiece>) {
      }

      override fun submitNameTag(
         poseStack: PoseStack,
         nameTagPosition: Vec3?,
         y: Int,
         nameTag: Component,
         notSneaking: Boolean,
         packedLight: Int,
         distanceToCameraSq: Double,
         cameraState: CameraRenderState
      ) {
      }

      override fun submitText(
         poseStack: PoseStack,
         x: Float,
         y: Float,
         text: FormattedCharSequence,
         dropShadow: Boolean,
         displayMode: DisplayMode,
         packedLight: Int,
         color: Int,
         backgroundColor: Int,
         outlineColor: Int
      ) {
      }

      override fun submitFlame(poseStack: PoseStack, renderState: EntityRenderState, rotation: Quaternionf) {
      }

      override fun submitLeash(poseStack: PoseStack, leashState: LeashState) {
      }

      override fun <S : Any> submitModel(
         model: Model<in S>,
         state: S,
         poseStack: PoseStack,
         renderType: RenderType,
         packedLight: Int,
         packedOverlay: Int,
         tintColor: Int,
         sprite: TextureAtlasSprite?,
         outlineColor: Int,
         crumblingOverlay: CrumblingOverlay?
      ) {
         super.submitModel(
            model,
            state,
            poseStack,
            renderType,
            packedLight,
            packedOverlay,
            this@ColorSubmitNodeStorage.colorArgb,
            sprite,
            0,
            crumblingOverlay
         )
      }

      override fun submitModelPart(
         part: ModelPart,
         poseStack: PoseStack,
         renderType: RenderType,
         packedLight: Int,
         packedOverlay: Int,
         sprite: TextureAtlasSprite?,
         xRot: Boolean,
         yRot: Boolean,
         tintColor: Int,
         crumblingOverlay: CrumblingOverlay?,
         outlineColor: Int
      ) {
         super.submitModelPart(
            part,
            poseStack,
            renderType,
            packedLight,
            packedOverlay,
            sprite,
            xRot,
            yRot,
            this@ColorSubmitNodeStorage.colorArgb,
            crumblingOverlay,
            outlineColor
         )
      }

      override fun submitBlock(poseStack: PoseStack, blockState: BlockState, x: Int, y: Int, z: Int) {
      }

      override fun submitMovingBlock(poseStack: PoseStack, renderState: MovingBlockRenderState) {
      }

      override fun submitBlockModel(
         poseStack: PoseStack,
         renderType: RenderType,
         model: BlockStateModel,
         x: Float,
         y: Float,
         z: Float,
         packedLight: Int,
         packedOverlay: Int,
         outlineColor: Int
      ) {
         val r = ARGB.red(this@ColorSubmitNodeStorage.colorArgb) / 255.0f
         val g = ARGB.green(this@ColorSubmitNodeStorage.colorArgb) / 255.0f
         val b = ARGB.blue(this@ColorSubmitNodeStorage.colorArgb) / 255.0f
         super.submitBlockModel(poseStack, renderType, model, r, g, b, packedLight, packedOverlay, outlineColor)
      }

      override fun submitItem(
         poseStack: PoseStack,
         displayContext: ItemDisplayContext,
         packedLight: Int,
         packedOverlay: Int,
         outlineColor: Int,
         tintColors: IntArray,
         quads: List<net.minecraft.client.renderer.block.model.BakedQuad>,
         renderType: RenderType,
         foilType: FoilType
      ) {
         if (this@ColorSubmitNodeStorage.itemTintColors == null ||
            this@ColorSubmitNodeStorage.itemTintColors!![0] != this@ColorSubmitNodeStorage.colorArgb) {
            this@ColorSubmitNodeStorage.itemTintColors = intArrayOf(
               this@ColorSubmitNodeStorage.colorArgb,
               this@ColorSubmitNodeStorage.colorArgb,
               this@ColorSubmitNodeStorage.colorArgb,
               this@ColorSubmitNodeStorage.colorArgb
            )
         }

         super.submitItem(
            poseStack,
            displayContext,
            packedLight,
            packedOverlay,
            outlineColor,
            this@ColorSubmitNodeStorage.itemTintColors!!,
            quads,
            renderType,
            foilType
         )
      }

      override fun submitCustomGeometry(poseStack: PoseStack, renderType: RenderType, renderer: SubmitNodeCollector.CustomGeometryRenderer) {
      }

      override fun submitParticleGroup(renderer: SubmitNodeCollector.ParticleGroupRenderer) {
      }
   }
}
