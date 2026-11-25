package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.utils.math.MathConstants
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items

internal class AutoCrystalTargeting(private val module: AutoCrystalModule) {
    private val targets = ArrayList<LivingEntity>()

    @Synchronized
    fun refresh() {
        this.targets.clear()
        val level = ClientAccess.collectiveMinecraft.level ?: return
        for (entity in level.entitiesForRendering()) {
            if (entity !is LivingEntity || this.module.isInvalidTarget(entity)) {
                continue
            }
            if (entity is Player) {
                this.targets.add(entity)
            }

            if (this.module.bossesSetting.value && (entity is WitherBoss || entity is EnderDragon || entity is Warden)) {
                this.targets.add(entity)
            }
        }
    }

    @Synchronized
    fun snapshot(): List<LivingEntity> {
        return ArrayList(this.targets)
    }

    fun findProximityTarget(): LivingEntity? {
        val currentTarget = this.module.target
        val previousTarget = this.module.previousTarget
        var proximityTarget = if (currentTarget == null && previousTarget != null && !this.module.isInvalidTarget(previousTarget)) {
            previousTarget
        } else {
            currentTarget
        }

        if (proximityTarget == null) {
            val player = ClientAccess.collectiveMinecraft.player ?: return null
            var nearestDistance = Double.MAX_VALUE
            for (candidate in this.snapshot()) {
                val distance = player.distanceTo(candidate).toDouble()
                if (!this.module.isInvalidTarget(candidate) && distance <= nearestDistance) {
                    nearestDistance = distance
                    proximityTarget = candidate
                }
            }

            if (proximityTarget == null) {
                return null
            }

            this.module.target = proximityTarget
        }
        return proximityTarget
    }

    fun hasObsidianDropNear(proximityTarget: LivingEntity): Boolean {
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        for (entity in level.entitiesForRendering()) {
            if (entity is ItemEntity) {
                val itemStack = entity.item
                if (itemStack.`is`(Items.OBSIDIAN)) {
                    val itemCount = itemStack.count
                    if (itemCount >= 8 && entity.isAlive) {
                        val targetBounds = proximityTarget.boundingBox
                        val itemBounds = entity.boundingBox
                        val targetCenter = targetBounds.center
                        val centerY = targetCenter.y
                        val upperTargetBounds = targetBounds.setMinY(centerY)
                        val expandZ = MathConstants.ONE_HALF
                        val expandX = MathConstants.ONE_HALF
                        val proximityBox = upperTargetBounds.inflate(expandX, 0.0, expandZ)
                        if (itemBounds.intersects(proximityBox)) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }
}
