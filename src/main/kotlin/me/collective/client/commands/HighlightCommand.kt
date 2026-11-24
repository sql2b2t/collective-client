package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.WorldRenderEvent
import me.collective.client.managers.RotationManager
import me.collective.client.render.ColorUtils
import me.collective.client.render.FeatureBoxRenderer
import me.collective.client.render.RenderUtils
import me.collective.client.render.WorldBoxRenderer
import me.collective.client.settings.ColorSetting
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.math.MathUtils
import me.collective.client.utils.math.Vec3Utils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

class HighlightCommand : Command("highlight") {
    private var trackedPosition: Vec3? = null

    init {
        val configuration = FeatureBoxRenderer.RenderConfiguration(
            this,
            { ColorUtils.withAlpha(FILL_SETTING.getColor(), 0.5f) },
            { TRANSPARENT_OUTLINE },
            { 1.0f },
            { 5.0f },
            { true },
            { true },
            configurationFadeTimeScale()
        )
        ClientContext.featureBoxRenderer.register(configuration)
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val positionArgument = argument("pos", PositionArgument()) as RequiredArgumentBuilder<Any?, *>

        val trackBuilder = literal<Any?>("track")
        val trackCallback = { context: com.mojang.brigadier.context.CommandContext<Any?> ->
            val position = context.getArgument("pos", Vec3::class.java)
            trackedPosition = position
            ClientAccess.eventBus.subscribe(this)
            1
        }
        val trackBranch = trackBuilder.executes(trackCallback)
        val highlightBuilder = positionArgument.then(trackBranch) as RequiredArgumentBuilder<Any?, *>

        val highlightCallback = { context: com.mojang.brigadier.context.CommandContext<Any?> ->
            val argumentPosition = context.getArgument("pos", Position::class.java)
            val blockPosition = BlockPos.containing(argumentPosition)
            val blockCenter = blockPosition.center

            val pointBox = AABB.ofSize(blockCenter, 0.5, 0.5, 0.5)
            ClientContext.featureBoxRenderer.addBox(this, pointBox)

            val topY = WorldUtils.getTopY()
            val level = ClientAccess.collectiveMinecraft.level
            val minY = level?.minY?.toDouble() ?: 0.0
            val columnBounds = AABB.ofSize(blockCenter, 0.25, 0.25, 0.25)
                .setMaxY(topY.toDouble())
                .setMinY(minY)

            ClientContext.featureBoxRenderer.addBox(this, columnBounds)
            1
        }
        val highlightBranch = highlightBuilder.executes(highlightCallback)
        root.then(highlightBranch)
    }

    @EventHandler
    fun onWorldRender(event: WorldRenderEvent.Main) {
        val targetPos = trackedPosition ?: return
        val gameRenderer = ClientAccess.collectiveMinecraft.gameRenderer
        val camera = gameRenderer.mainCamera
        val cameraPosition = camera.position()

        val distance = Vec3Utils.horizontalDistance(cameraPosition, targetPos)

        if (distance <= 2.0f) {
            trackedPosition = null
            ClientAccess.eventBus.unsubscribe(this)
        } else {
            var renderPosition = targetPos

            if (distance >= 120.0f) {
                val rotations = RotationManager.calculateRotations(cameraPosition, targetPos)
                val cameraX = cameraPosition.x
                val xRadius = 100.0f
                val xYaw = rotations[0] + MathConstants.NINETY_DEGREES
                val xYawRadians = Math.toRadians(xYaw.toDouble())
                val renderX = cameraX + xRadius * cos(xYawRadians)
                val renderY = targetPos.y
                val cameraZ = cameraPosition.z

                val zYaw = rotations[0] + MathConstants.NINETY_DEGREES
                val zYawRadians = Math.toRadians(zYaw.toDouble())
                renderPosition = Vec3(renderX, renderY, cameraZ + 100.0f * sin(zYawRadians))
            }

            val distanceLabel = "[%.0fm]".format(distance)
            var fadeAmount = distance - 2.0f
            fadeAmount = Mth.clamp(fadeAmount, 0.0f, 9.0f)
            val opacity = fadeAmount / 9.0f

            val topY = WorldUtils.getTopY()
            val level = ClientAccess.collectiveMinecraft.level
            val minY = level?.minY?.toDouble() ?: 0.0
            val column = AABB.ofSize(renderPosition, 0.25, 0.25, 0.25)
                .setMaxY(topY.toDouble())
                .setMinY(minY)

            val fillColor = ColorUtils.withAlpha(FILL_SETTING.getColor(), opacity * 0.3f)
            val labelColor = ColorUtils.withAlpha(Color.WHITE, opacity)

            WorldBoxRenderer.fillBox(event.poseStack, column, fillColor)
            RenderUtils.INSTANCE.drawWorldText(
                event.poseStack,
                distanceLabel,
                renderPosition,
                MathUtils.getDistanceScale(renderPosition, 1.7),
                labelColor,
                true
            )
        }
    }

    companion object {
        private val FILL_SETTING = ColorSetting("Fill", Color.WHITE).apply {
            isSynced = true
        }
        private val TRANSPARENT_OUTLINE = Color(0, 0, 0, 0)

        private fun configurationFadeTimeScale(): Int {
            return 1000
        }
    }
}
