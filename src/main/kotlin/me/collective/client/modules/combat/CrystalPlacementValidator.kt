package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.PlayerState
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB

class CrystalPlacementValidator : ClientAccess {
    private var requireUpperAir = false
    private var checkEntityCollisions = true
    private var blockNearbyCrystals = false
    private var allowFire = true
    private var lowerHitbox = false
    private var expandMovingPlayers = false
    private var ignoreBaseBlock = false
    private var ignoreYoungItems = false
    private var predictionTicks = 0
    private var ignoredCrystalRangeSquared = 16.0f

    fun canPlace(base: BlockPos): Boolean {
        if (!this.ignoreBaseBlock) {
            if (!WorldUtils.isBedrockOrObsidian(base)) {
                return false
            }
        }

        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val crystalSpace = base.offset(0, 1, 0)

        val spaceState = level.getBlockState(crystalSpace)
        val spaceBlock = spaceState.block
        var clearSpace: Boolean = if (spaceBlock is AirBlock) {
            true
        } else {
            crystalSpace == ClientContext.worldStateManager.unconfirmedBreak
        }

        val fluid = spaceState.fluidState
        if (!fluid.isEmpty) {
            clearSpace = false
        }

        if (this.allowFire) {
            if (!clearSpace && spaceBlock != Blocks.FIRE) {
                return false
            }
        } else if (!clearSpace) {
            return false
        }

        if (this.requireUpperAir) {
            val upperSpace = base.offset(0, 2, 0)
            val upperState = level.getBlockState(upperSpace)
            if (upperState.block !is AirBlock) {
                return false
            }
        }

        val spaceBounds = AABB(crystalSpace)
        val spaceY = crystalSpace.y
        val hitboxHeight = if (this.lowerHitbox) 1 else 2

        val top = (spaceY + hitboxHeight).toDouble()
        val collisionBox = spaceBounds.setMaxY(top)
        var collisionCount = 0

        val localPlayer = ClientAccess.collectiveMinecraft.player

        for (entity in level.entitiesForRendering()) {
            if (entity == null || entity.isRemoved || !entity.isAlive) {
                continue
            }

            var entityBounds = entity.boundingBox
            if (entity is Player && entity != localPlayer) {
                entityBounds = BoundingBoxUtils.getInterpolationBounds(entity as LivingEntity)
                if (this.expandMovingPlayers) {
                    if (!(entity as PlayerState).`collective$isNextToWall`()) {
                        entityBounds = entityBounds.inflate(0.0001, 0.0, 0.0001)
                    }
                }

                for (predictionTick in 1..this.predictionTicks) {
                    val predictedBox = ClientContext.movementPredictionManager.getPredictedBoundingBox(entity, predictionTick)
                    val currentTop = entityBounds.maxY
                    val predictedTop = predictedBox.setMaxY(currentTop)
                    val currentBottom = entityBounds.minY
                    val predictedBounds = predictedTop.setMinY(currentBottom)
                    if (predictedBounds.intersects(collisionBox)) {
                        collisionCount++
                    }
                }
            } else {
                if (entity is EndCrystal && !this.blockNearbyCrystals) {
                    if (localPlayer != null && localPlayer.eyePosition.distanceToSqr(entity.position()) <= this.ignoredCrystalRangeSquared) {
                        continue
                    }
                }

                if (entity is ItemEntity && this.ignoreYoungItems) {
                    val itemAge = entity.tickCount
                    if (itemAge < 3) {
                        continue
                    }
                }
            }

            if (entityBounds.intersects(collisionBox)) {
                collisionCount++
                break
            }
        }

        return !(this.checkEntityCollisions && collisionCount != 0)
    }

    fun requireUpperAir(enabled: Boolean): CrystalPlacementValidator {
        this.requireUpperAir = enabled
        return this
    }

    fun checkEntityCollisions(enabled: Boolean): CrystalPlacementValidator {
        this.checkEntityCollisions = enabled
        return this
    }

    fun blockNearbyCrystals(enabled: Boolean): CrystalPlacementValidator {
        this.blockNearbyCrystals = enabled
        return this
    }

    fun allowFire(enabled: Boolean): CrystalPlacementValidator {
        this.allowFire = enabled
        return this
    }

    fun lowerHitbox(enabled: Boolean): CrystalPlacementValidator {
        this.lowerHitbox = enabled
        return this
    }

    fun expandMovingPlayers(enabled: Boolean): CrystalPlacementValidator {
        this.expandMovingPlayers = enabled
        return this
    }

    fun ignoreBaseBlock(enabled: Boolean): CrystalPlacementValidator {
        this.ignoreBaseBlock = enabled
        return this
    }

    fun ignoreYoungItems(enabled: Boolean): CrystalPlacementValidator {
        this.ignoreYoungItems = enabled
        return this
    }

    fun predictionTicks(ticks: Int): CrystalPlacementValidator {
        this.predictionTicks = ticks
        return this
    }

    fun ignoredCrystalRange(range: Float): CrystalPlacementValidator {
        this.ignoredCrystalRangeSquared = range * range
        return this
    }
}
