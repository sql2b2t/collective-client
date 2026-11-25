package me.collective.client.managers

import me.collective.client.ClientAccess
import me.collective.client.access.LocalPlayerTickAccess
import me.collective.mixin.LivingEntityAccessor
import net.minecraft.world.phys.Vec3

class PlayerTickExecutor private constructor() : ClientAccess {
    companion object {
        private var runningExtraTick: Boolean = false

        @JvmStatic
        fun isRunningExtraTick(): Boolean = runningExtraTick

        @JvmStatic
        fun runExtraTick() {
            val player = ClientAccess.collectiveMinecraft.player ?: return
            runningExtraTick = true
            try {
                val useItemRemainingTicks = player.useItemRemainingTicks
                val moveDistance = player.moveDist
                val walkAnimationSpeed = player.walkAnimation.speed()
                val walkDistance = player.walkDist
                val previousWalkDistance = player.walkDistO
                val verticalBob = player.yBob
                val previousVerticalBob = player.yBobO
                val horizontalBob = player.xBob
                val previousHorizontalBob = player.xBobO
                val previousPosition = Vec3(player.xo, player.yo, player.zo)
                val attackStrengthTicks = player.attackStrengthTicker
                val swingTime = player.swingTime
                val previousAttackAnimation = player.oAttackAnim
                val attackAnimation = player.attackAnim

                val tickAccess = player as LocalPlayerTickAccess
                tickAccess.superTick()
                tickAccess.resetEvent()

                player.attackStrengthTicker = attackStrengthTicks
                (player as LivingEntityAccessor).setItemUseTimeLeft(useItemRemainingTicks)
                player.moveDist = moveDistance
                player.walkAnimation.setSpeed(walkAnimationSpeed)
                player.walkDist = walkDistance
                player.walkDistO = previousWalkDistance
                player.yBob = verticalBob
                player.yBobO = previousVerticalBob
                player.xBob = horizontalBob
                player.xBobO = previousHorizontalBob
                player.swingTime = swingTime
                player.oAttackAnim = previousAttackAnimation
                player.attackAnim = attackAnimation

                tickAccess.sendMovementPacketsWrapper()
                tickAccess.resetRotations()

                player.xo = previousPosition.x
                player.yo = previousPosition.y
                player.zo = previousPosition.z
            } finally {
                runningExtraTick = false
            }
        }
    }
}
