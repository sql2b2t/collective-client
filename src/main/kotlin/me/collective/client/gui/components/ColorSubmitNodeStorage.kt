package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.Font.DisplayMode
import net.minecraft.client.model.Model
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.SubmitNodeCollection
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeStorage
import net.minecraft.client.renderer.block.MovingBlockRenderState
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.item.ItemDisplayContext
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
      return this.getSubmitsPerOrder().computeIfAbsent(order) { ColoredSubmitNodeCollection() }
   }

   private inner class ColoredSubmitNodeCollection : SubmitNodeCollection() {
      override fun submitShadow(poseStack: PoseStack, shadowRadius: Float, shadowPieces: List<EntityRenderState.ShadowPiece>) {
      }

      override fun submitNameTag(
         poseStack: PoseStack,
         nameTagPosition: Vec3?,
         y: Int,
         nameTag: Component,
         notSneaking: Boolean,
         packedLight: Int,
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

      override fun submitMovingBlock(poseStack: PoseStack, renderState: MovingBlockRenderState, outlineColor: Int) {
      }

      override fun submitBlockModel(
         poseStack: PoseStack,
         renderType: RenderType,
         parts: List<BlockStateModelPart>,
         tintLayers: IntArray,
         packedLight: Int,
         packedOverlay: Int,
         outlineColor: Int
      ) {
         super.submitBlockModel(poseStack, renderType, parts, tintLayers, packedLight, packedOverlay, outlineColor)
      }

      override fun submitItem(
         poseStack: PoseStack,
         displayContext: ItemDisplayContext,
         packedLight: Int,
         packedOverlay: Int,
         outlineColor: Int,
         tintColors: IntArray,
         quads: List<BakedQuad>,
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
            foilType
         )
      }

      override fun submitCustomGeometry(poseStack: PoseStack, renderType: RenderType, renderer: SubmitNodeCollector.CustomGeometryRenderer) {
      }
   }
}
