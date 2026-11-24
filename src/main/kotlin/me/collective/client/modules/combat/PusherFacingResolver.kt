package me.collective.client.modules.combat

import net.minecraft.core.Direction
import net.minecraft.util.Mth

class PusherFacingResolver internal constructor() {
    fun resolveFacing(angles: FloatArray): Direction {
        val pitchRadians = angles[1] * 0.017453292f
        val negativeYawRadians = -angles[0] * 0.017453292f
        val sinPitch = Mth.sin(pitchRadians.toDouble())
        val cosPitch = Mth.cos(pitchRadians.toDouble())
        val sinYaw = Mth.sin(negativeYawRadians.toDouble())
        val cosYaw = Mth.cos(negativeYawRadians.toDouble())
        val positiveX = sinYaw > 0.0f
        val upward = sinPitch < 0.0f
        val positiveZ = cosYaw > 0.0f
        val absSinYaw = if (positiveX) sinYaw else -sinYaw
        val absSinPitch = if (upward) -sinPitch else sinPitch
        val absCosYaw = if (positiveZ) cosYaw else -cosYaw
        val xProjection = absSinYaw * cosPitch
        val zProjection = absCosYaw * cosPitch
        val xDirection = if (positiveX) Direction.EAST else Direction.WEST
        val yDirection = if (upward) Direction.UP else Direction.DOWN
        val zDirection = if (positiveZ) Direction.SOUTH else Direction.NORTH
        return if (absSinYaw > absCosYaw) {
            if (absSinPitch > xProjection) yDirection else xDirection
        } else {
            if (absSinPitch > zProjection) yDirection else zDirection
        }
    }
}
