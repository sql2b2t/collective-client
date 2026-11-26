package me.collective.client.gui.screens

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.client.UIModule
import me.collective.client.render.GuiDrawUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.math.MathUtils
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import kotlin.math.max
import kotlin.math.min

class SnowflakeParticle : ClientAccess {
    private val fallSpeed: Float
    private val scale: Float
    val initialRelativeX: Double
        @JvmName("getInitialRelativeXProp") get

    private val horizontalDrift: Float
    private var relativeY: Float

    val isExpired: Boolean
        @JvmName("isExpiredProp") get() = this.relativeY >= 1.0f

    fun isExpired(): Boolean = isExpired

    init {
        val randomSpeed = Math.random()
        this.fallSpeed = Mth.clamp(randomSpeed, 0.07000000029802322, 0.18000000715255737).toFloat()
        val minimumScale = MathConstants.HALF.toFloat()
        this.scale = MathUtils.randomFloat(minimumScale, 1.15f)
        this.initialRelativeX = Math.random()
        this.horizontalDrift = MathUtils.randomFloat(-0.033f, 0.033f)
        this.relativeY = MathUtils.randomFloat(-0.45f, 0.0f)
    }

    fun getInitialRelativeX(): Double = initialRelativeX

    fun render(poseStack: PoseStack) {
        val relativeX = this.initialRelativeX + this.relativeY * this.horizontalDrift
        val screenWidth = ClientAccess.collectiveMinecraft.window.guiScaledWidth
        val screenHeight = ClientAccess.collectiveMinecraft.window.guiScaledHeight
        val screenX = (relativeX * screenWidth).toFloat()
        var renderValue = this.relativeY * screenHeight * 0.5f - 9.0f
        val screenY = renderValue - 1.0f

        val frameTiming = ClientContext.frameTiming
        this.relativeY += frameTiming.getFrameFactor(0.5f) * this.fallSpeed
        this.relativeY = min(this.relativeY, 1.0f)

        val snowEnabled = UIModule.INSTANCE.snowSetting.value
        if (snowEnabled && this.relativeY >= 0.0f) {
            val opacity = Mth.ceil(max(255.0f * (1.0f - this.relativeY), 4.0f))
            poseStack.pushPose()
            poseStack.scale(this.scale, this.scale, 1.0f)
            poseStack.translate(screenX / this.scale, screenY / this.scale, 0.0f)
            GuiDrawUtils.drawText(poseStack, "❆", 0, 0, ARGB.color(opacity, -1), true)
            poseStack.popPose()
        }
    }
}
