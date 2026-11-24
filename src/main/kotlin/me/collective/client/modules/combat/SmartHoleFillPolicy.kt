package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.math.Vec3Utils
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.world.HoleInfo
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB

class SmartHoleFillPolicy(holeFillModule: HoleFillModule) : PlainHoleFillPolicy(holeFillModule) {

    override fun shouldFill(hole: HoleInfo): Boolean {
        val levelSnapshot = ClientAccess.collectiveMinecraft.level ?: return false

        for (player in levelSnapshot.players()) {
            val selfSafetySettingRef = holeFillModule.selfSafetySetting
            if (selfSafetySettingRef.value && !isSelfSafetySatisfied(hole)) {
                continue
            }

            val ignoreNakedsSettingRef = holeFillModule.ignoreNakedsSetting
            if (ignoreNakedsSettingRef.value && !PlayerStateUtils.hasArmor(player)) {
                continue
            }

            if (player.isDeadOrDying || ClientAccess.collectiveMinecraft.player == player) {
                continue
            }

            if (ClientContext.socialRegistry.isFriendPlayer(player)) {
                continue
            }

            val holeManagerSnapshot = ClientContext.holeManager
            val playerBlockPosSnapshot = BlockPos.containing(player.position())

            if (holeManagerSnapshot.intersectsHole(playerBlockPosSnapshot) || !super.shouldFill(hole)) {
                continue
            }

            val extrapolationSettingRef = holeFillModule.extrapolationSetting
            if (!extrapolationSettingRef.value) {
                val currentBounds = BoundingBoxUtils.getInterpolationBounds(player)
                if (!shouldIgnorePlayerBounds(hole, player, currentBounds)) {
                    return true
                }
                continue
            }

            val ticksSettingRef = holeFillModule.ticksSetting
            // Хотя бы одна итерация (включая Ticks=1 и «Auto»=0) и проверка вплоть до 1-го тика:
            // раньше цикл при predictionTicks <= 1 не выполнялся и Smart-режим не заполнял дыры.
            var predictionTicks = Math.max(1, BoundingBoxUtils.getPredictionTicks(ticksSettingRef))
            while (predictionTicks > 0) {
                val predictedBounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(player, predictionTicks)
                if (!shouldIgnorePlayerBounds(hole, player, predictedBounds)) {
                    return true
                }
                predictionTicks--
            }
        }
        return false
    }

    private fun shouldIgnorePlayerBounds(hole: HoleInfo, player: Player, bounds: AABB): Boolean {
        var playerConditionResult = true

        if (MovementPhysics.isMoving(player)) {
            val conditionHoleCenter = hole.pos.getCenter()
            playerConditionResult = MovementPhysics.isMovingToward(player, conditionHoleCenter)
        }

        val boundsCenter = BoundingBoxUtils.getBottomCenter(bounds)
        val holeCenterSnapshot = hole.pos.getCenter()
        val boundsCenterSnapshot = BoundingBoxUtils.getBottomCenter(bounds)

        val smartDistance = Vec3Utils.horizontalDistance(holeCenterSnapshot, boundsCenterSnapshot)
        val smartRange: Float = holeFillModule.smartRangeSetting.value
        if (smartDistance <= smartRange) {
            val centerY = boundsCenter.y()
            val heightHolePos = hole.pos
            if (centerY > heightHolePos.getY()) {
                val boundsMinY = bounds.minY
                val heightDifference = boundsMinY - heightHolePos.getY()

                if (heightDifference <= 4.0 && playerConditionResult) {
                    return false
                }
            }
        }

        return true
    }

    private fun isSelfSafetySatisfied(hole: HoleInfo): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return true
        val holeManagerSnapshot = ClientContext.holeManager
        val playerBlockPosSnapshot = BlockPos.containing(player.position())

        if (!holeManagerSnapshot.intersectsHole(playerBlockPosSnapshot)) {
            val playerPositionSnapshot = player.position()
            val holeCenter = hole.pos.getCenter()
            val distance = playerPositionSnapshot.distanceTo(holeCenter)
            if (distance < 2.0) {
                return false
            }
        }

        return true
    }
}
