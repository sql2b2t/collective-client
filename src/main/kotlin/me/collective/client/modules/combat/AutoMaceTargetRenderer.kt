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
import me.collective.client.utils.ElapsedTimer
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import java.util.function.Supplier

class AutoMaceTargetRenderer(private val module: AutoMaceModule) : ClientAccess {
    private val markers = ArrayList<TargetMarker>()
    private val markerTimer = ElapsedTimer()

    fun addTargetMarker(target: Entity?) {
        if (target != null) {
            if (this.markerTimer.hasElapsedAndReset(MARKER_LIFETIME_MILLIS)) {
                val positionSupplier = Supplier<Vec3> {
                    val tickDelta = RenderUtils.getPartialTick()
                    target.getPosition(tickDelta)
                }

                val targetWidth = target.bbWidth
                val radius = targetWidth * 2.0f
                val marker = TargetMarker(positionSupplier, radius, System.currentTimeMillis() + MARKER_LIFETIME_MILLIS)
                this.markers.add(marker)
            }
        }
    }

    fun reset() {
        this.markers.clear()
        this.markerTimer.clear()
    }

    private fun drawMarker(
        poseStack: PoseStack,
        vertices: MeshBuffer,
        marker: TargetMarker,
        segments: Int
    ) {
        val pose = poseStack.last()

        val camera = ClientAccess.collectiveMinecraft.entityRenderDispatcher.camera ?: return
        val cameraPosition = camera.position()
        val markerPosition = marker.positionSupplier.get()
        val relativeVector = markerPosition.subtract(cameraPosition)
        val relativePosition = relativeVector.toVector3f()

        val expiresAtMillis = marker.expiresAtMillis
        val remainingRatio = (expiresAtMillis - System.currentTimeMillis()).toFloat() / 1500.0f
        val progress = 1.0f - Mth.clamp(remainingRatio, 0.0f, 1.0f)
        val radius = progress * marker.radius

        val configuredArgb = this.module.colorSetting.value.hashCode()
        val alpha = 1.0f - progress
        val color = ARGB.multiplyAlpha(configuredArgb, alpha)

        VertexGeometry.forEachCircleSegment(radius, segments) { firstPoint, secondPoint ->
            val firstX = relativePosition.x + firstPoint.x
            val firstY = relativePosition.y
            val firstZ = relativePosition.z + firstPoint.z
            val secondX = relativePosition.x + secondPoint.x
            val secondY = relativePosition.y
            val secondZ = relativePosition.z + secondPoint.z
            VertexGeometry.appendLine(vertices, pose, firstX, firstY, firstZ, secondX, secondY, secondZ, 1.0f, color)
        }
    }

    @EventHandler
    fun renderMarkers(event: WorldRenderEvent.Main) {
        this.markers.removeIf { expiredMarker -> System.currentTimeMillis() >= expiredMarker.expiresAtMillis }
        if (this.markers.isNotEmpty()) {
            val poseStack = event.poseStack ?: return
            val buffer = MeshDrawContext.getBuffer()
            buffer.begin(PrimitiveTopology.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
            for (marker in this.markers) {
                val vertices = MeshDrawContext.getBuffer()
                this.drawMarker(poseStack, vertices, marker, 32)
            }
            MeshDrawContext.getBuffer().draw(ClientRenderTypes.worldLines())
        }
    }

    private data class TargetMarker(
        val positionSupplier: Supplier<Vec3>,
        val radius: Float,
        val expiresAtMillis: Long
    )

    companion object {
        private const val MARKER_LIFETIME_MILLIS = 1500L
    }
}
