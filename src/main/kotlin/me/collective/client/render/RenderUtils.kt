package me.collective.client.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.render.Tesselator
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.math.Axis
import java.awt.Color
import java.util.OptionalInt
import me.collective.client.ClientAccess
import me.collective.mixin.GameRendererAccessor
import net.minecraft.client.gui.Font.DisplayMode
import me.collective.client.render.MultiBufferSource
import me.collective.client.render.MultiBufferSource.BufferSource
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.lwjgl.opengl.GL32C

class RenderUtils private constructor() : ClientAccess {
    fun renderEntity(
        entity: Entity, partialTick: Float, poses: PoseStack, buffers: MultiBufferSource
    ) {
        val dispatcher = ClientAccess.collectiveMinecraft.entityRenderDispatcher
        val cameraPosition = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera().position()
        val entityX = Mth.lerp(partialTick.toDouble(), entity.xOld, entity.x)
        val entityY = Mth.lerp(partialTick.toDouble(), entity.yOld, entity.y)
        val entityZ = Mth.lerp(partialTick.toDouble(), entity.zOld, entity.z)
        val renderState = dispatcher.extractEntity(entity, partialTick)
        val cameraRenderState = ClientAccess.collectiveMinecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState
        dispatcher.submit(
            renderState,
            cameraRenderState,
            entityX - cameraPosition.x,
            entityY - cameraPosition.y,
            entityZ - cameraPosition.z,
            poses,
            entitySubmitStorage
        )
    }

    fun drawRectOutline(
        poses: PoseStack, left: Float, top: Float, right: Float, bottom: Float, color: Color
    ) {
        var resolvedLeft = left
        var resolvedTop = top
        var resolvedRight = right
        var resolvedBottom = bottom
        if (resolvedRight < resolvedLeft) {
            val previousLeft = resolvedLeft
            resolvedLeft = resolvedRight
            resolvedRight = previousLeft
        }

        if (resolvedBottom < resolvedTop) {
            val previousTop = resolvedTop
            resolvedTop = resolvedBottom
            resolvedBottom = previousTop
        }

        drawHorizontalLine(poses, resolvedLeft, resolvedRight, resolvedTop, color)
        drawVerticalLine(poses, resolvedRight, resolvedTop, resolvedBottom, color)
        drawHorizontalLine(poses, resolvedLeft, resolvedRight, resolvedBottom, color)
        drawVerticalLine(poses, resolvedLeft, resolvedTop, resolvedBottom, color)
    }

    internal fun drawHorizontalLine(
        poses: PoseStack, left: Float, right: Float, y: Float, color: Color
    ) {
        var resolvedLeft = left
        var resolvedRight = right
        if (resolvedRight < resolvedLeft) {
            val previousLeft = resolvedLeft
            resolvedLeft = resolvedRight
            resolvedRight = previousLeft
        }

        fillRect(poses, resolvedLeft, y, resolvedRight + 1.0f, y + 1.0f, color)
    }

    fun drawWorldText(
        poses: PoseStack, text: String, position: Vec3, scale: Double, color: Color, shadow: Boolean
    ) {
        drawWorldText(poses, text, position, 0.0f, 0.0f, scale, color, shadow)
    }

    fun drawWorldTexture(
        poses: PoseStack,
        position: Vec3,
        width: Float,
        height: Float,
        scale: Double,
        color: Color,
        texture: Identifier
    ) {
        poses.pushPose()
        translateToCamera(poses, position)
        poses.mulPose(ClientAccess.collectiveMinecraft.entityRenderDispatcher.camera!!.rotation())
        poses.scale(0.025f * scale.toFloat(), -0.025f * scale.toFloat(), 1.0f)
        val halfWidth = (width / 2.0f).toInt()
        val halfHeight = (height / 2.0f).toInt()
        GuiDrawUtils.drawTexture(
            poses,
            texture,
            -halfWidth,
            -halfHeight,
            0.0f,
            0.0f,
            width.toInt(),
            height.toInt(),
            width.toInt(),
            height.toInt(),
            color.hashCode()
        )
        getEntityBuffers().endBatch()
        poses.popPose()
    }

    fun drawWorldRect(
        poses: PoseStack,
        position: Vec3,
        offsetX: Float,
        offsetY: Float,
        width: Float,
        height: Float,
        scale: Double,
        color: Color
    ) {
        poses.pushPose()
        translateToCamera(poses, position)
        poses.mulPose(ClientAccess.collectiveMinecraft.entityRenderDispatcher.camera!!.rotation())
        poses.translate(offsetX, offsetY, 0.0f)
        poses.scale(0.025f * scale.toFloat(), -0.025f * scale.toFloat(), 0.025f * scale.toFloat())
        val halfWidth = (width / 2.0f).toInt()
        fillRect(poses, -halfWidth.toFloat(), 0.0f, (width - halfWidth).toInt().toFloat(), height.toInt().toFloat(), color)
        poses.popPose()
    }

    fun fillRect(
        poses: PoseStack, left: Float, top: Float, right: Float, bottom: Float, z: Int, color: Int
    ) {
        var resolvedLeft = left
        var resolvedTop = top
        var resolvedRight = right
        var resolvedBottom = bottom
        if (resolvedLeft < resolvedRight) {
            val previousLeft = resolvedLeft
            resolvedLeft = resolvedRight
            resolvedRight = previousLeft
        }

        if (resolvedTop < resolvedBottom) {
            val previousTop = resolvedTop
            resolvedTop = resolvedBottom
            resolvedBottom = previousTop
        }

        val vertices = Tesselator.getInstance().begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR)
        val matrix = poses.last().pose()
        vertices.addVertex(matrix, resolvedLeft, resolvedBottom, z.toFloat()).setColor(color)
        vertices.addVertex(matrix, resolvedRight, resolvedBottom, z.toFloat()).setColor(color)
        vertices.addVertex(matrix, resolvedRight, resolvedTop, z.toFloat()).setColor(color)
        vertices.addVertex(matrix, resolvedLeft, resolvedTop, z.toFloat()).setColor(color)
        MeshBuffer.drawMesh(ClientRenderTypes.gui(), vertices.buildOrThrow())
    }

    fun fillRect(
        poses: PoseStack, left: Float, top: Float, right: Float, bottom: Float, color: Int
    ) {
        fillRect(poses, left, top, right, bottom, 0, color)
    }

    fun drawWorldText(
        poses: PoseStack,
        text: String,
        position: Vec3,
        offsetX: Float,
        offsetY: Float,
        textX: Float,
        textY: Float,
        scale: Double,
        color: Color,
        shadow: Boolean
    ) {
        poses.pushPose()
        translateToCamera(poses, position)
        poses.mulPose(ClientAccess.collectiveMinecraft.entityRenderDispatcher.camera!!.rotation())
        poses.translate(offsetX, offsetY, 0.0f)
        poses.scale(0.025f * scale.toFloat(), -0.025f * scale.toFloat(), 0.025f * scale.toFloat())
        if (shadow) {
            FontRenderer.INSTANCE.drawWithShadow(poses, text, textX, textY, color, DisplayMode.SEE_THROUGH)
        } else {
            FontRenderer.INSTANCE.drawWithoutShadow(poses, text, textX, textY, color.hashCode(), DisplayMode.SEE_THROUGH)
        }

        poses.popPose()
    }

    fun drawWorldText(
        poses: PoseStack,
        text: String,
        position: Vec3,
        offsetX: Float,
        offsetY: Float,
        scale: Double,
        color: Color,
        shadow: Boolean
    ) {
        drawWorldText(
            poses,
            text,
            position,
            offsetX,
            offsetY,
            -FontRenderer.INSTANCE.getWidth(text) / 2.0f,
            0.0f,
            scale,
            color,
            shadow
        )
    }

    fun addWorldLine(
        poses: PoseStack,
        vertices: BufferBuilder,
        from: Vec3,
        to: Vec3,
        width: Float,
        startColor: Int,
        endColor: Int
    ) {
        val cameraPosition = ClientAccess.collectiveMinecraft.entityRenderDispatcher.camera!!.position()
        val fromX = (from.x() - cameraPosition.x()).toFloat()
        val fromY = (from.y() - cameraPosition.y()).toFloat()
        val fromZ = (from.z() - cameraPosition.z()).toFloat()
        val toX = (to.x() - cameraPosition.x()).toFloat()
        val toY = (to.y() - cameraPosition.y()).toFloat()
        val toZ = (to.z() - cameraPosition.z()).toFloat()
        VertexGeometry.appendLine(vertices, poses.last(), fromX, fromY, fromZ, toX, toY, toZ, width, startColor, endColor)
    }

    fun fillRect(
        poses: PoseStack, left: Float, top: Float, right: Float, bottom: Float, color: Color
    ) {
        fillRect(poses, left, top, right, bottom, color.hashCode())
    }

    internal fun drawVerticalLine(
        poses: PoseStack, x: Float, top: Float, bottom: Float, color: Color
    ) {
        var resolvedTop = top
        var resolvedBottom = bottom
        if (resolvedBottom < resolvedTop) {
            val previousTop = resolvedTop
            resolvedTop = resolvedBottom
            resolvedBottom = previousTop
        }

        fillRect(poses, x, resolvedTop + 1.0f, x + 1.0f, resolvedBottom, color)
    }

    fun flushEntities() {
        val target = ClientAccess.collectiveMinecraft.gameRenderer.mainRenderTarget()
        val prevColor = RenderSystem.outputColorTextureOverride
        val prevDepth = RenderSystem.outputDepthTextureOverride
        RenderSystem.outputColorTextureOverride = target.colorTextureView
        RenderSystem.outputDepthTextureOverride = if (target.useDepth) target.depthTextureView else null
        try {
            ClientAccess.collectiveMinecraft.gameRenderer.featureRenderDispatcher().renderAllFeatures(entitySubmitStorage)
            entitySubmitStorage.getSubmitsPerOrder().clear()
        } finally {
            RenderSystem.outputColorTextureOverride = prevColor
            RenderSystem.outputDepthTextureOverride = prevDepth
        }
    }

    companion object {
        private val entitySubmitStorage = net.minecraft.client.renderer.SubmitNodeStorage()
        @JvmStatic
        fun getEntitySubmitStorage(): net.minecraft.client.renderer.SubmitNodeStorage = entitySubmitStorage
        @JvmField
        val INSTANCE: RenderUtils = RenderUtils()

        @JvmField
        val WORLD_PROJECTION_MATRIX: Matrix4f = Matrix4f()

        private val renderColor = IntArray(4)
        private var partialTick: Float = 0.0f
        private var inverseViewBobbing: Boolean = false

        @JvmStatic
        fun setColorAlpha(alpha: Int) {
            setColorComponents(renderColor[0], renderColor[1], renderColor[2], alpha)
        }

        @JvmStatic
        fun getEntityBuffers(): BufferSource =
            MultiBufferSource.immediate()

        @JvmStatic
        fun setLineSmoothing(enabled: Boolean) {
            if (enabled) {
                GL32C.glEnable(GL32C.GL_LINE_SMOOTH)
            } else {
                GL32C.glDisable(GL32C.GL_LINE_SMOOTH)
            }
        }

        @JvmStatic
        fun setPartialTick(framePartialTick: Float) {
            partialTick = framePartialTick
        }

        @JvmStatic
        fun getPartialTick(): Float = partialTick

        @JvmStatic
        fun setLineWidth(width: Float) {
            GL32C.glLineWidth(width)
        }

        @JvmStatic
        @JvmOverloads
        fun clearColor(target: RenderTarget, color: Color = Color.white) {
            val clearVec = org.joml.Vector4f(color.red / 255.0f, color.green / 255.0f, color.blue / 255.0f, 0.0f)
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.colorTexture!!, clearVec)
        }

        @JvmStatic
        fun translateToCamera(poses: PoseStack, position: Vec3) {
            val cameraPosition = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera().position()
            val relativeX = position.x - cameraPosition.x
            val relativeY = position.y - cameraPosition.y
            val relativeZ = position.z - cameraPosition.z
            poses.translate(relativeX, relativeY, relativeZ)
        }

        @JvmStatic
        fun applyInverseViewBobbing(poses: PoseStack, partialTick: Float) {
            inverseViewBobbing = true
            if (ClientAccess.collectiveMinecraft.options.bobView().get()) {
                val cameraState = ClientAccess.collectiveMinecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState
                (ClientAccess.collectiveMinecraft.gameRenderer as GameRendererAccessor).`collective$bobView`(cameraState, poses)
            }

            inverseViewBobbing = false
        }

        @JvmStatic
        @JvmOverloads
        fun blitToMain(
            target: RenderTarget?,
            pipeline: RenderPipeline = ClientRenderPipelines.BLIT_TRANSLUCENT,
            filter: FilterMode = FilterMode.NEAREST
        ) {
            if (target == null) {
                return
            }
            val indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
            val indexBuffer = indices.getBuffer(6)
            val outputTexture = ClientAccess.collectiveMinecraft.gameRenderer.mainRenderTarget().colorTextureView!!

            RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass({ "Blit render target" }, outputTexture, java.util.Optional.empty())
                .use { renderPass ->
                    renderPass.setPipeline(pipeline)
                    RenderSystem.bindDefaultUniforms(renderPass)
                    renderPass.setIndexBuffer(indexBuffer, indices.type())
                    renderPass.bindTexture(
                        "InSampler",
                        target.colorTextureView,
                        RenderSystem.getSamplerCache().getClampToEdge(filter)
                    )
                    renderPass.drawIndexed(6, 1, 0, 0, 0)
                }
        }

        @JvmStatic
        fun setRenderColor(red: Float, green: Float, blue: Float, alpha: Float) {
            renderColor[0] = (red * 255.0f).toInt()
            renderColor[1] = (green * 255.0f).toInt()
            renderColor[2] = (blue * 255.0f).toInt()
            renderColor[3] = (alpha * 255.0f).toInt()
        }

        @JvmStatic
        fun clearDepth(target: RenderTarget) {
            RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(target.depthTexture!!, 0.0)
        }

        @JvmStatic
        fun getRenderColor(): IntArray = renderColor

        @JvmStatic
        fun setColorComponents(red: Int, green: Int, blue: Int, alpha: Int) {
            renderColor[0] = red
            renderColor[1] = green
            renderColor[2] = blue
            renderColor[3] = alpha
        }

        @JvmStatic
        fun applyCameraRotation(poses: PoseStack) {
            val camera = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera()
            poses.mulPose(Axis.XP.rotationDegrees(camera.xRot()))
            poses.mulPose(Axis.YP.rotationDegrees(camera.yRot() + 180.0f))
        }

        @JvmStatic
        fun applyCameraRotation(matrix: Matrix4f) {
            val camera = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera()
            matrix.rotate(Axis.XP.rotationDegrees(camera.xRot()))
            matrix.rotate(Axis.YP.rotationDegrees(camera.yRot() + 180.0f))
        }

        @JvmStatic
        fun setColorAlpha(alpha: Float) {
            setColorComponents(renderColor[0], renderColor[1], renderColor[2], (alpha * 255.0f).toInt())
        }

        @JvmStatic
        fun isInverseViewBobbing(): Boolean = inverseViewBobbing
    }
}
