package me.collective.client.render

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat.Mode
import java.awt.Color
import me.collective.client.ClientAccess
import net.minecraft.CrashReport
import net.minecraft.ReportedException
import net.minecraft.client.gui.Font.DisplayMode
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f

object GuiDrawUtils : ClientAccess {
    @JvmField
    internal val quadBuffer: MeshBuffer = MeshBuffer.create()

    @JvmField
    internal val lineBuffer: MeshBuffer = MeshBuffer.create()

    @JvmField
    internal var lineWidth: Float = 1.0f

    init {
        quadBuffer.setFormat(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        lineBuffer.setFormat(Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL)
    }

    @JvmStatic
    @JvmOverloads
    fun flush(flushText: Boolean = true) {
        MeshDrawContext.setRenderType(ClientRenderTypes.gui())
        quadBuffer.draw()
        MeshDrawContext.setRenderType(ClientRenderTypes.worldLines())
        lineBuffer.draw()
        if (flushText) {
            FontRenderer.INSTANCE.flush()
        }
    }

    @JvmStatic
    fun getLineVertices(): VertexConsumer = lineBuffer

    private fun fillVerticalStrip(
        poseStack: PoseStack, x: Float, top: Float, bottom: Float, thickness: Float, color: Color
    ) {
        fillRect(poseStack, x, top + thickness, x + thickness, bottom, color)
    }

    private fun drawTexturedQuad(
        poseStack: PoseStack,
        texture: ResourceLocation,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        minU: Float,
        maxU: Float,
        minV: Float,
        maxV: Float,
        color: Int
    ) {
        val renderType = ClientRenderTypes.image(texture, false)
        val matrix = poseStack.last().pose()
        val vertices = ClientAccess.collectiveMinecraft.renderBuffers()
            .bufferSource()
            .getBuffer(renderType)
        vertices.addVertex(matrix, left.toFloat(), top.toFloat(), 0.0f).setUv(minU, minV).setColor(color)
        vertices.addVertex(matrix, left.toFloat(), bottom.toFloat(), 0.0f).setUv(minU, maxV).setColor(color)
        vertices.addVertex(matrix, right.toFloat(), bottom.toFloat(), 0.0f).setUv(maxU, maxV).setColor(color)
        vertices.addVertex(matrix, right.toFloat(), top.toFloat(), 0.0f).setUv(maxU, minV).setColor(color)
    }

    @JvmStatic
    fun drawTexture(
        poseStack: PoseStack,
        texture: ResourceLocation,
        x: Int,
        y: Int,
        u: Float,
        v: Float,
        width: Int,
        height: Int,
        regionWidth: Int,
        regionHeight: Int,
        textureWidth: Int,
        textureHeight: Int,
        color: Int
    ) {
        drawTexturedQuad(
            poseStack,
            texture,
            x,
            x + width,
            y,
            y + height,
            (u + 0.0f) / textureWidth,
            (u + regionWidth) / textureWidth,
            (v + 0.0f) / textureHeight,
            (v + regionHeight) / textureHeight,
            color
        )
    }

    @JvmStatic
    fun drawTexture(
        poseStack: PoseStack,
        texture: ResourceLocation,
        x: Int,
        y: Int,
        u: Float,
        v: Float,
        width: Int,
        height: Int,
        textureWidth: Int,
        textureHeight: Int,
        color: Int
    ) {
        drawTexture(
            poseStack, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, color
        )
    }

    @JvmStatic
    @JvmOverloads
    fun drawItem(
        poseStack: PoseStack,
        stack: ItemStack,
        x: Float,
        y: Float,
        z: Float,
        flushItemBuffers: Boolean = true
    ) {
        if (stack.isEmpty) {
            return
        }

        val renderState = net.minecraft.client.renderer.item.ItemStackRenderState()
        ClientAccess.collectiveMinecraft.itemModelResolver.updateForTopItem(
            renderState,
            stack,
            ItemDisplayContext.GUI,
            false,
            ClientAccess.collectiveMinecraft.level,
            null,
            0
        )

        val depthScale: Int
        if (renderState.usesBlockLight()) {
            Lighting.setupFor3DItems()
            depthScale = 16
        } else {
            Lighting.setupForFlatItems()
            depthScale = 1
        }

        poseStack.pushPose()
        poseStack.translate(x + 8.0f, y + 8.0f, 150.0f + z)

        try {
            poseStack.scale(16.0f, -16.0f, depthScale.toFloat())
            renderState.render(
                poseStack,
                RenderUtils.getEntityBuffers(),
                15728880,
                OverlayTexture.NO_OVERLAY
            )
            if (flushItemBuffers) {
                RenderUtils.INSTANCE.flushEntities()
            }
        } catch (failure: Throwable) {
            throw ReportedException(CrashReport.forThrowable(failure, "Rendering item"))
        }

        poseStack.popPose()
    }

    @JvmStatic
    fun drawDurabilityBar(
        poseStack: PoseStack, stack: ItemStack, x: Float, y: Float
    ) {
        if (stack.isBarVisible) {
            val barX = x + 2.0f
            val barY = y + 13.0f
            poseStack.translate(0.0f, 0.0f, 1.0f)
            fillRect(poseStack, barX, barY, barX + 13.0f, barY + 2.0f, Color.black)
            fillRect(poseStack, barX, barY, barX + stack.barWidth, barY + 1.0f, ARGB.opaque(stack.barColor))
            poseStack.translate(0.0f, 0.0f, -1.0f)
        }
    }

    @JvmStatic
    fun fillGradientRect(
        matrix: Matrix4f,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        topLeftColor: Int,
        topRightColor: Int,
        bottomRightColor: Int,
        bottomLeftColor: Int
    ) {
        quadBuffer.addVertex(matrix, x, y + height, 0.0f).setColor(bottomLeftColor)
        quadBuffer.addVertex(matrix, x + width, y + height, 0.0f).setColor(bottomRightColor)
        quadBuffer.addVertex(matrix, x + width, y, 0.0f).setColor(topRightColor)
        quadBuffer.addVertex(matrix, x, y, 0.0f).setColor(topLeftColor)
    }

    @JvmStatic
    fun fillHorizontalGradient(
        matrix: Matrix4f, x: Float, y: Float, width: Float, height: Float, vararg colors: Int
    ) {
        if (colors.size < 2) {
            return
        }

        val segmentWidth = width / (colors.size - 1)
        for (index in 1 until colors.size) {
            val segmentEnd = if (index == colors.size - 1) width else segmentWidth * index
            val rightColor = colors[index]
            val leftColor = colors[index - 1]
            quadBuffer.addVertex(matrix, x + segmentEnd, y, 0.0f).setColor(rightColor)
            quadBuffer.addVertex(matrix, x + segmentWidth * (index - 1), y, 0.0f).setColor(leftColor)
            quadBuffer.addVertex(matrix, x + segmentWidth * (index - 1), y + height, 0.0f).setColor(leftColor)
            quadBuffer.addVertex(matrix, x + segmentEnd, y + height, 0.0f).setColor(rightColor)
        }
    }

    @JvmStatic
    fun drawText(
        poseStack: PoseStack, text: String, x: Int, y: Int, color: Int, shadow: Boolean
    ) {
        if (shadow) {
            FontRenderer.INSTANCE
        }
        ClientAccess.collectiveMinecraft.font.drawInBatch(
            text,
            x.toFloat(),
            y.toFloat(),
            color,
            shadow,
            poseStack.last().pose(),
            FontRenderer.INSTANCE.getRenderer().getBufferSource(),
            DisplayMode.NORMAL,
            0,
            15728880
        )
    }

    @JvmStatic
    fun drawLineRect(
        poseStack: PoseStack, left: Float, top: Float, right: Float, bottom: Float, color: Color
    ) {
        val packedColor = color.hashCode()
        if (color.alpha != 0) {
            VertexGeometry.appendLine(lineBuffer, poseStack.last(), left, bottom, 0.0f, right, bottom, 0.0f, lineWidth, packedColor)
            VertexGeometry.appendLine(lineBuffer, poseStack.last(), right, bottom, 0.0f, right, top, 0.0f, lineWidth, packedColor)
            VertexGeometry.appendLine(lineBuffer, poseStack.last(), right, top, 0.0f, left, top, 0.0f, lineWidth, packedColor)
            VertexGeometry.appendLine(lineBuffer, poseStack.last(), left, top, 0.0f, left, bottom, 0.0f, lineWidth, packedColor)
        }
    }

    /** Прямой отрезок в line-буфер; width — толщина штриха в пикселях GUI. */
    @JvmStatic
    fun drawLine(
        poseStack: PoseStack, x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int
    ) {
        VertexGeometry.appendLine(lineBuffer, poseStack.last(), x1, y1, 0.0f, x2, y2, 0.0f, width, color)
    }

    /** Рамка скруглённого прямоугольника: 4 прямых отрезка + 4 дуги (по segments сегментов на четверть), штрих 1px. */
    @JvmStatic
    fun drawRoundedBorder(
        poseStack: PoseStack, left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int, segments: Int
    ) {
        if (radius < 1.0f) {
            drawLine(poseStack, left, top, right, top, 1.0f, color)
            drawLine(poseStack, right, top, right, bottom, 1.0f, color)
            drawLine(poseStack, right, bottom, left, bottom, 1.0f, color)
            drawLine(poseStack, left, bottom, left, top, 1.0f, color)
            return
        }

        val clampedRadius = minOf(radius, minOf(right - left, bottom - top) / 2.0f)
        drawLine(poseStack, left + clampedRadius, top, right - clampedRadius, top, 1.0f, color)
        drawLine(poseStack, right, top + clampedRadius, right, bottom - clampedRadius, 1.0f, color)
        drawLine(poseStack, right - clampedRadius, bottom, left + clampedRadius, bottom, 1.0f, color)
        drawLine(poseStack, left, bottom - clampedRadius, left, top + clampedRadius, 1.0f, color)
        val halfPi = (Math.PI / 2.0).toFloat()
        appendBorderArc(poseStack, left + clampedRadius, top + clampedRadius, clampedRadius, Math.PI.toFloat(), segments, color)
        appendBorderArc(poseStack, right - clampedRadius, top + clampedRadius, clampedRadius, -halfPi, segments, color)
        appendBorderArc(poseStack, right - clampedRadius, bottom - clampedRadius, clampedRadius, 0.0f, segments, color)
        appendBorderArc(poseStack, left + clampedRadius, bottom - clampedRadius, clampedRadius, halfPi, segments, color)
    }

    /** Четверть дуги рамки от угла startAngle против часовой стрелки (cos/sin по четвертям). */
    private fun appendBorderArc(
        poseStack: PoseStack, centerX: Float, centerY: Float, radius: Float, startAngle: Float, segments: Int, color: Int
    ) {
        val step = (Math.PI / 2.0).toFloat() / segments
        var previousX = centerX + radius * Mth.cos(startAngle)
        var previousY = centerY + radius * Mth.sin(startAngle)
        for (index in 1..segments) {
            val angle = startAngle + step * index
            val currentX = centerX + radius * Mth.cos(angle)
            val currentY = centerY + radius * Mth.sin(angle)
            drawLine(poseStack, previousX, previousY, currentX, currentY, 1.0f, color)
            previousX = currentX
            previousY = currentY
        }
    }

    @JvmStatic
    fun drawRectOutline(
        poseStack: PoseStack, left: Float, top: Float, right: Float, bottom: Float, color: Color
    ) {
        fillHorizontalStrip(poseStack, left, right, top, 1.0f, color)
        fillVerticalStrip(poseStack, right, top, bottom, 1.0f, color)
        fillHorizontalStrip(poseStack, left, right, bottom, 1.0f, color)
        fillVerticalStrip(poseStack, left, top, bottom, 1.0f, color)
    }

    @JvmStatic
    fun drawItemCooldown(
        poseStack: PoseStack, stack: ItemStack, x: Float, y: Float
    ) {
        val player = ClientAccess.collectiveMinecraft.player
        val partialTick = ClientAccess.collectiveMinecraft.deltaTracker.getGameTimeDeltaPartialTick(true)
        val cooldown = if (player == null) 0.0f else player.cooldowns.getCooldownPercent(stack, partialTick)
        if (cooldown > 0.0f) {
            val top = y + Mth.floor(16.0f * (1.0f - cooldown))
            val bottom = top + Mth.ceil(16.0f * cooldown)
            fillRect(poseStack, x, top, x + 16.0f, bottom, 0x7FFFFFFF)
        }
    }

    @JvmStatic
    fun fillRect(
        poseStack: PoseStack, left: Float, top: Float, right: Float, bottom: Float, color: Int
    ) {
        if (ARGB.alpha(color) != 0) {
            val matrix = poseStack.last().pose()
            quadBuffer.addVertex(matrix, left, bottom, 0.0f).setColor(color)
            quadBuffer.addVertex(matrix, right, bottom, 0.0f).setColor(color)
            quadBuffer.addVertex(matrix, right, top, 0.0f).setColor(color)
            quadBuffer.addVertex(matrix, left, top, 0.0f).setColor(color)
        }
    }

    private fun fillQuad(
        matrix: Matrix4f, x1: Float, y1: Float, x2: Float, y2: Float, color: Int
    ) {
        quadBuffer.addVertex(matrix, x1, y2, 0.0f).setColor(color)
        quadBuffer.addVertex(matrix, x2, y2, 0.0f).setColor(color)
        quadBuffer.addVertex(matrix, x2, y1, 0.0f).setColor(color)
        quadBuffer.addVertex(matrix, x1, y1, 0.0f).setColor(color)
    }

    private fun fillCornerFan(
        matrix: Matrix4f, centerX: Float, centerY: Float, radius: Float, startAngle: Float, sweep: Float, segments: Int, color: Int
    ) {
        var previousX = centerX + radius * Mth.cos(startAngle)
        var previousY = centerY + radius * Mth.sin(startAngle)
        val step = sweep / segments
        for (index in 1..segments) {
            val angle = startAngle + step * index
            val currentX = centerX + radius * Mth.cos(angle)
            val currentY = centerY + radius * Mth.sin(angle)
            quadBuffer.addVertex(matrix, centerX, centerY, 0.0f).setColor(color)
            quadBuffer.addVertex(matrix, previousX, previousY, 0.0f).setColor(color)
            quadBuffer.addVertex(matrix, currentX, currentY, 0.0f).setColor(color)
            quadBuffer.addVertex(matrix, currentX, currentY, 0.0f).setColor(color)
            previousX = currentX
            previousY = currentY
        }
    }

    /** Скруглённый прямоугольник: центр + 4 кромки + 4 угловых веера. Радиус клампится в половину меньшей стороны. */
    @JvmStatic
    fun fillRoundedRect(
        poseStack: PoseStack, left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int
    ) {
        if (ARGB.alpha(color) == 0) {
            return
        }

        val width = right - left
        val height = bottom - top
        if (width <= 0.0f || height <= 0.0f) {
            return
        }

        val clampedRadius = minOf(radius, minOf(width, height) / 2.0f)
        if (clampedRadius <= 0.5f) {
            fillRect(poseStack, left, top, right, bottom, color)
            return
        }

        val matrix = poseStack.last().pose()
        fillQuad(matrix, left + clampedRadius, top, right - clampedRadius, bottom, color)
        fillQuad(matrix, left, top + clampedRadius, left + clampedRadius, bottom - clampedRadius, color)
        fillQuad(matrix, right - clampedRadius, top + clampedRadius, right, bottom - clampedRadius, color)
        fillQuad(matrix, left + clampedRadius, top, right - clampedRadius, top + clampedRadius, color)
        fillQuad(matrix, left + clampedRadius, bottom - clampedRadius, right - clampedRadius, bottom, color)
        val segments = maxOf(3, Mth.ceil(clampedRadius * 0.9f))
        val halfPi = (Math.PI / 2.0).toFloat()
        fillCornerFan(matrix, left + clampedRadius, top + clampedRadius, clampedRadius, Math.PI.toFloat(), halfPi, segments, color)
        fillCornerFan(matrix, right - clampedRadius, top + clampedRadius, clampedRadius, -halfPi, halfPi, segments, color)
        fillCornerFan(matrix, right - clampedRadius, bottom - clampedRadius, clampedRadius, 0.0f, halfPi, segments, color)
        fillCornerFan(matrix, left + clampedRadius, bottom - clampedRadius, clampedRadius, halfPi, halfPi, segments, color)
    }

    /** Заполненный круг (квад-веер вокруг центра). */
    @JvmStatic
    fun fillCircle(
        poseStack: PoseStack, centerX: Float, centerY: Float, radius: Float, color: Int
    ) {
        fillRoundedRect(poseStack, centerX - radius, centerY - radius, centerX + radius, centerY + radius, radius, color)
    }

    @JvmStatic
    fun fillVerticalGradient(
        poseStack: PoseStack, x: Float, y: Float, width: Float, height: Float, topColor: Int, bottomColor: Int
    ) {
        fillGradientRect(poseStack.last().pose(), x, y, width, height, topColor, topColor, bottomColor, bottomColor)
    }

    @JvmStatic
    fun fillRect(
        poseStack: PoseStack, left: Float, top: Float, right: Float, bottom: Float, color: Color
    ) {
        fillRect(poseStack, left, top, right, bottom, color.hashCode())
    }

    private fun fillHorizontalStrip(
        poseStack: PoseStack, left: Float, right: Float, y: Float, thickness: Float, color: Color
    ) {
        fillRect(poseStack, left, y, right + thickness, y + thickness, color)
    }

    @JvmStatic
    fun fillHorizontalGradient(
        poseStack: PoseStack, x: Float, y: Float, width: Float, height: Float, leftColor: Int, rightColor: Int
    ) {
        fillGradientRect(poseStack.last().pose(), x, y, width, height, leftColor, rightColor, rightColor, leftColor)
    }
}
