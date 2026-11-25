package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.WorldRenderEvent
import me.collective.client.managers.RotationManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.render.RenderUtils
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.inventory.ItemUtils
import me.collective.client.utils.math.MathConstants
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.AxeItem
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

class AimAssistModule : Module("AimAssist", "Helps you aiming at players.", ModuleCategory.COMBAT) {
    private val rangeSetting: Setting<Float> = add(NumberSetting("Range", 4.0f, 1.0f, 6.0f).withUnit("m"))
    private val speedSetting: Setting<Float> = add(NumberSetting("Speed", 1.0f, 0.1f, 15.0f))
    private val fovSetting: Setting<Float> = add(NumberSetting("Fov", 90.0f, 1.0f, 360.0f))
    private val onlyWeaponSetting: Setting<Boolean> = add(BooleanSetting("OnlyWeapon", true))
    private val ignoreInvisibleSetting: Setting<Boolean> = add(BooleanSetting("IgnoreInvisible", true))
    private val lockTargetSetting: Setting<Boolean> = add(BooleanSetting("LockTarget", true))
    private val friendsSetting: Setting<Boolean> = add(BooleanSetting("Friends", true))
    private val instantSetting: Setting<Boolean> = add(BooleanSetting("Instant", false))

    private var targetPlayer: AbstractClientPlayer? = null

    @EventHandler
    private fun onWorldRender(event: WorldRenderEvent.Main) {
        if (ClientAccess.collectiveMinecraft.screen != null) {
            return
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return
        val level = ClientAccess.collectiveMinecraft.level ?: return

        val mainHandItem = player.mainHandItem
        if (mainHandItem.item !is AxeItem && !ItemUtils.isSword(mainHandItem)) {
            if (onlyWeaponSetting.value) {
                return
            }
        }

        val keepTarget = targetPlayer != null && lockTargetSetting.value
        if (!keepTarget) {
            targetPlayer = level.players().stream()
                .sorted(Comparator.comparingDouble { candidate -> player.distanceTo(candidate).toDouble() })
                .filter { isValidTarget(it) }
                .findFirst()
                .orElse(null)
        }

        val checkedTarget = targetPlayer
        if (!isValidTarget(checkedTarget)) {
            targetPlayer = null
            return
        }

        if (ClientAccess.collectiveMinecraft.crosshairPickEntity == checkedTarget) {
            return
        }

        val aimedTarget = checkedTarget ?: return

        if (instantSetting.value) {
            val bounds = aimedTarget.boundingBox
            val center = bounds.center
            val rotations = RotationManager.getRotationsToPosition(center)
            player.yRot = rotations[0]
            player.xRot = rotations[1]
        } else {
            val yawDifference = yawDifference(aimedTarget)
            if (yawDifference <= 1.0 && yawDifference >= -1.0) {
                return
            }

            val speedValue = speedSetting.value
            val scaledDifference = yawDifference * speedValue
            val yawStep = (scaledDifference * 0.009999999776482582).toFloat()
            val partialTick = RenderUtils.getPartialTick()
            val nextYaw = player.getViewYRot(partialTick) - yawStep
            player.yRot = nextYaw
        }
    }

    private fun isValidTarget(entity: Entity?): Boolean {
        if (entity !is Player || entity === ClientAccess.collectiveMinecraft.player) {
            return false
        }

        if (!entity.isAlive) {
            return false
        }

        if (ignoreInvisibleSetting.value && entity.isInvisible) {
            return false
        }

        val fieldOfView = fovSetting.value.toDouble()
        if (!isWithinFov(entity, fieldOfView)) {
            return false
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val distance = player.distanceTo(entity)
        if (distance > rangeSetting.value) {
            return false
        }

        if (friendsSetting.value) {
            val name = entity.gameProfile.name
            if (ClientContext.socialRegistry.isFriendName(name)) {
                return false
            }
        }

        return hasLineOfSight(entity)
    }

    private fun hasLineOfSight(entity: Entity): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val level = ClientAccess.collectiveMinecraft.level ?: return false

        val start = Vec3(player.x, player.y + player.eyeHeight, player.z)
        val feet = Vec3(entity.x, entity.y, entity.z)
        val feetRay = ClipContext(start, feet, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)

        val feetHit = level.clip(feetRay)
        if (feetHit.type == HitResult.Type.MISS) {
            return true
        }

        val eyes = Vec3(entity.x, entity.y + entity.eyeHeight, entity.z)
        val eyesRay = ClipContext(start, eyes, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
        val eyesHit = level.clip(eyesRay)
        return eyesHit.type == HitResult.Type.MISS
    }

    companion object {
        private fun yawDifference(entity: Entity): Double {
            val player = ClientAccess.collectiveMinecraft.player ?: return 0.0
            val partialTick = RenderUtils.getPartialTick()
            val viewYaw = player.getViewYRot(partialTick)
            val difference = (viewYaw - yawToEntity(entity)) % MathConstants.FULL_TURN_DEGREES
            val wrapped = (difference + 540.0) % MathConstants.FULL_TURN_DEGREES
            return wrapped - 180.0
        }

        private fun isWithinFov(entity: Entity, fieldOfView: Double): Boolean {
            val halfFov = fieldOfView * MathConstants.ONE_HALF
            val player = ClientAccess.collectiveMinecraft.player ?: return false
            val partialTick = RenderUtils.getPartialTick()
            val viewYaw = player.getViewYRot(partialTick)
            val difference = (viewYaw - yawToEntity(entity)) % MathConstants.FULL_TURN_DEGREES
            val wrapped = (difference + 540.0) % MathConstants.FULL_TURN_DEGREES
            val yawDiff = wrapped - 180.0
            return (yawDiff > 0.0 && yawDiff < halfFov) || (-halfFov < yawDiff && yawDiff < 0.0)
        }

        private fun yawToEntity(entity: Entity): Float {
            val player = ClientAccess.collectiveMinecraft.player ?: return 0.0f
            val deltaX = entity.x - player.x
            val deltaZ = entity.z - player.z
            val angle = Math.atan2(deltaX, deltaZ)
            val degrees = angle * 57.2957795
            return (degrees * -1.0).toFloat()
        }
    }
}
