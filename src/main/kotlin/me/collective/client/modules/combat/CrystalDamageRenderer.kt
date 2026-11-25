package me.collective.client.modules.combat

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.events.EventHandler
import me.collective.client.events.WorldRenderEvent
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.RenderUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.awt.Color

class CrystalDamageRenderer(private val autoCrystal: AutoCrystalModule) {
    private var damage: Float = 0.0f
    private var visibleUntilMillis: Long = 0L
    private var pos: BlockPos? = null

    fun showDamage(base: BlockPos, newDamage: Double) {
        this.pos = base
        val now = System.currentTimeMillis()
        this.visibleUntilMillis = now + 150L
        this.damage = newDamage.toFloat()
    }

    @EventHandler(priority = Int.MIN_VALUE)
    fun onRenderDamage(event: WorldRenderEvent.Main) {
        if (!autoCrystal.renderDamageSetting.value) {
            return
        }

        val fadeDuration = autoCrystal.fadeTimeSetting.value
        var fadeMillis = (1000.0f * fadeDuration).toLong()

        if (!autoCrystal.fadeSetting.value) {
            fadeMillis = 1L
        }

        if (System.currentTimeMillis() >= this.visibleUntilMillis + fadeMillis) {
            return
        }

        val text = String.format("%.1f", this.damage)
        val elapsedMillis = System.currentTimeMillis() - this.visibleUntilMillis
        val elapsed = elapsedMillis.toFloat()

        val duration = autoCrystal.fadeTimeSetting.value
        val alpha = 1.0f - elapsed / (duration * 1000.0f)
        if (alpha <= 0.02f) {
            return
        }

        val basePos = this.pos ?: return
        val poseStack: PoseStack = event.poseStack ?: return
        val center: Vec3 = basePos.getCenter()
        val negativeWidth = -FontRenderer.INSTANCE.getWidth(text)
        val textX = negativeWidth / 2.0f
        val negativeHeight = -FontRenderer.INSTANCE.getHeight()
        val textY = negativeHeight / 2.0f

        RenderUtils.INSTANCE.drawWorldText(
            poseStack,
            text,
            center,
            0.0f,
            0.0f,
            textX,
            textY,
            1.0,
            ColorUtils.withAlpha(Color.WHITE, alpha),
            true
        )
    }
}
