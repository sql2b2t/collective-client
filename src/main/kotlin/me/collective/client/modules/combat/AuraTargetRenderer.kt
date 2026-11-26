package me.collective.client.modules.combat

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.PrimitiveTopology
import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.WorldRenderEvent
import me.collective.client.render.ClientRenderTypes
import me.collective.client.render.MeshBuffer
import me.collective.client.render.MeshDrawContext
import me.collective.client.render.RenderUtils
import me.collective.client.render.VertexGeometry
import me.collective.client.render.WorldBoxRenderer
import me.collective.client.settings.options.AuraTypeChoice
import me.collective.client.utils.FloatAnimation
import me.collective.client.utils.combat.BoundingBoxUtils
import net.minecraft.util.ARGB
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import org.joml.Vector3f

internal class AuraTargetRenderer(private val aura: AuraModule) : ClientAccess {
    private val fadeAnimation = FloatAnimation()
    private var targetBox: AABB? = null

    fun updateTarget(target: Entity?) {
        if (target == null) {
            fadeAnimation.setTarget(0.0f, 250L)
        } else {
            if (target is LivingEntity && target.hurtDuration == target.hurtTime) {
                fadeAnimation.setValue(1.0f)
            } else {
                fadeAnimation.setTarget(0.5f, 250L)
            }
            val partialTick = RenderUtils.getPartialTick()
            targetBox = BoundingBoxUtils.getRenderBounds(target, partialTick)
        }
    }

    private fun drawTargetVertices(
        poseStack: PoseStack,
        buffer: MeshBuffer,
        filled: Boolean,
        bounds: AABB,
        segments: Int,
        color: Int,
        lineWidth: Float
    ) {
        val pose = poseStack.last()
        val matrix = pose.pose()

        val cyclePhase = (System.currentTimeMillis() % 3000L).toFloat() / 1500.0f
        val firstHalf = cyclePhase < 1.0f
        val mirroredPhase = if (cyclePhase > 1.0f) 2.0f - cyclePhase else cyclePhase

        val cosine = Math.cos(Math.PI * mirroredPhase)
        val easedHeight = -(cosine - 1.0)
        val heightProgress = (easedHeight / 2.0).toFloat()
        val width = bounds.xsize
        val horizontalSize = width + bounds.zsize

        val radius = Math.min((horizontalSize / 2.0).toFloat(), 4.0f)

        val camera = ClientAccess.collectiveMinecraft.entityRenderDispatcher.camera ?: return
        val cameraPosition = camera.position()
        val bottomCenter = bounds.bottomCenter
        val relativeCenter = bottomCenter.subtract(cameraPosition)
        val relativePosition = relativeCenter.toVector3f()
        val boxHeight = bounds.ysize

        val verticalOffset = (heightProgress.toDouble() * (boxHeight - 0.1)).toFloat() + 0.2f
        val ringPosition = relativePosition.add(0.0f, verticalOffset, 0.0f)
        VertexGeometry.forEachCircleSegment(radius, segments) { firstPoint, secondPoint ->
            if (filled) {
                var trailFade = heightProgress * 2.0f
                if (trailFade > 1.0f) {
                    trailFade = 2.0f
                    trailFade -= heightProgress * 2.0f
                } else {
                    trailFade = heightProgress * 2.0f
                }
                val direction = if (firstHalf) -1.0f else 1.0f
                val trailHeight = direction * (0.3f * trailFade)
                var vertexX = ringPosition.x + firstPoint.x
                var vertexY = ringPosition.y
                var vertexZ = ringPosition.z + firstPoint.z
                var vertex = buffer.addVertex(matrix, vertexX, vertexY, vertexZ)
                vertex.setColor(color)
                vertexX = ringPosition.x + firstPoint.x
                vertexY = ringPosition.y + trailHeight
                vertexZ = ringPosition.z + firstPoint.z
                val firstTopVertex = buffer.addVertex(matrix, vertexX, vertexY, vertexZ)
                val firstTopColor = ARGB.color(0, color)
                firstTopVertex.setColor(firstTopColor)
                vertexX = ringPosition.x + secondPoint.x
                vertexY = ringPosition.y + trailHeight
                vertexZ = ringPosition.z + secondPoint.z
                val secondTopVertex = buffer.addVertex(matrix, vertexX, vertexY, vertexZ)
                val secondTopColor = ARGB.color(0, color)
                secondTopVertex.setColor(secondTopColor)
                vertexX = ringPosition.x + secondPoint.x
                vertexY = ringPosition.y
                vertexZ = ringPosition.z + secondPoint.z
                vertex = buffer.addVertex(matrix, vertexX, vertexY, vertexZ)
                vertex.setColor(color)
            } else {
                VertexGeometry.appendLine(
                    buffer,
                    pose,
                    ringPosition.x + firstPoint.x,
                    ringPosition.y,
                    ringPosition.z + firstPoint.z,
                    ringPosition.x + secondPoint.x,
                    ringPosition.y,
                    ringPosition.z + secondPoint.z,
                    lineWidth,
                    color
                )
            }
        }
    }

    private fun drawBox(
        event: WorldRenderEvent,
        bounds: AABB,
        fillColor: Int,
        outlineColor: Int
    ) {
        WorldBoxRenderer.fillBox(event.poseStack, bounds, fillColor)
        val outlinePoseStack = event.poseStack

        val lineWidth = aura.lineWidthSetting.value
        WorldBoxRenderer.outlineBox(outlinePoseStack, bounds, outlineColor, lineWidth)
    }

    @EventHandler
    fun renderTarget(event: WorldRenderEvent.Main) {
        if (aura.renderSetting.value) {
            val target = aura.target
            updateTarget(target)
            renderTarget(event as WorldRenderEvent)
        }
    }

    fun renderTarget(event: WorldRenderEvent) {
        val fade = fadeAnimation.value
        val bounds = targetBox
        if (bounds != null && fade != 0.0f) {
            val fillColor = ARGB.multiplyAlpha(aura.fillSetting.value.hashCode(), fade)
            val outlineColor = ARGB.multiplyAlpha(aura.outlineSetting.value.hashCode(), fade)
            if (aura.typeSetting.value == AuraTypeChoice.BOX) {
                drawBox(event, bounds, fillColor, outlineColor)
            } else {
                drawWireBox(event, bounds, fillColor, outlineColor)
            }
        }
    }

    private fun drawWireBox(event: WorldRenderEvent, bounds: AABB, fillColor: Int, outlineColor: Int) {
        val poseStack = event.poseStack ?: return
        val buffer = MeshDrawContext.getBuffer()
        buffer.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR)
        drawTargetVertices(poseStack, buffer, true, bounds, 32, fillColor, 1.0f)
        buffer.draw(ClientRenderTypes.worldFill())
        buffer.begin(PrimitiveTopology.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
        drawTargetVertices(poseStack, buffer, false, bounds, 32, outlineColor, aura.lineWidthSetting.value)
        buffer.draw(ClientRenderTypes.worldLines())
    }
}
