package me.collective.client.bots.follow

import com.mojang.authlib.GameProfile
import me.collective.client.ClientAccess
import me.collective.client.bots.BotClient
import me.collective.client.bots.BotManager
import me.collective.client.bots.session.BotSessionManager
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object BotFollowManager : ClientAccess {
    private val activeFollowers: MutableSet<BotClient> = Collections.newSetFromMap(ConcurrentHashMap())
    @Volatile var mirrorTarget: String = ""
        private set
    @Volatile var followTarget: String = ""
        private set
    @Volatile private var isTargetLocated: Boolean = false
    @Volatile private var targetX: Int = 0
    @Volatile private var targetY: Int = 0
    @Volatile private var targetZ: Int = 0

    val isMirroring: Boolean get() = mirrorTarget.isNotEmpty()
    val isFollowing: Boolean get() = followTarget.isNotEmpty()

    fun startMirroring(playerName: String) {
        stopFollowing()
        mirrorTarget = playerName
    }

    fun startFollowing(playerName: String) {
        stopMirroring()
        isTargetLocated = false
        followTarget = playerName
    }

    fun stopMirroring() {
        if (mirrorTarget.isNotEmpty()) {
            mirrorTarget = ""
            resetAllBotInputs()
        }
    }

    fun stopFollowing() {
        followTarget = ""
        isTargetLocated = false
    }

    fun stopAll() {
        stopMirroring()
        stopFollowing()
    }

    fun unregisterBot(bot: BotClient) {
        activeFollowers.remove(bot)
    }

    fun tick() {
        if (isMirroring) {
            tickMirroring()
        } else if (isFollowing) {
            tickFollowing()
        }
    }

    private fun tickMirroring() {
        val player = findPlayer(mirrorTarget)
        if (player == null) {
            resetAllBotInputs()
            return
        }

        val input = extractPlayerInput(player)
        val yaw = player.yRot
        val pitch = player.xRot

        for (bot in BotManager.bots) {
            if (bot.connectionState.isInGame) {
                bot.inputController.setKeys(
                    input.forward(),
                    input.backward(),
                    input.left(),
                    input.right(),
                    input.jump(),
                    input.shift(),
                    input.sprint()
                )
                bot.playerState.setRotation(yaw, pitch)
            }
        }
    }

    private fun tickFollowing() {
        val player = findPlayer(followTarget)
        if (player == null) {
            isTargetLocated = false
            return
        }

        val pos = player.blockPosition()
        targetX = pos.x
        targetY = pos.y
        targetZ = pos.z
        isTargetLocated = true

        for (bot in BotManager.bots) {
            if (bot.connectionState.isInGame) {
                updateBotPathing(bot, pos)
            }
        }
    }

    private fun updateBotPathing(bot: BotClient, targetPos: BlockPos) {
        val localBotPlayer = bot.worldState.botLocalPlayer ?: return
        val dx = targetPos.x + 0.5 - localBotPlayer.x
        val dy = targetPos.y - localBotPlayer.y
        val dz = targetPos.z + 0.5 - localBotPlayer.z
        val distSq = dx * dx + dz * dz

        if (distSq > 4.0) {
            // Aim at target
            val targetYaw = (Math.toDegrees(Math.atan2(dz, dx)) - 90.0).toFloat()
            val targetPitch = (-Math.toDegrees(Math.atan2(dy, Math.sqrt(distSq)))).toFloat()
            bot.playerState.setRotation(targetYaw, targetPitch)
            bot.inputController.setKeys(
                forward = true,
                backward = false,
                left = false,
                right = false,
                jump = localBotPlayer.horizontalCollision || (dy > 0.5 && localBotPlayer.onGround()),
                sneak = false,
                sprint = distSq > 16.0
            )
        } else {
            bot.inputController.reset()
        }
    }

    private fun extractPlayerInput(player: Player): Input {
        if (player is LocalPlayer) {
            return player.input.keyPresses
        }
        val delta = player.position().subtract(player.xo, player.yo, player.zo)
        val rad = Math.toRadians(player.yRot.toDouble())
        val sin = Math.sin(rad)
        val cos = Math.cos(rad)
        val forwardDelta = cos * delta.z - sin * delta.x
        val strafeDelta = cos * delta.x + sin * delta.z

        return Input(
            forwardDelta > 0.015,
            forwardDelta < -0.015,
            strafeDelta > 0.015,
            strafeDelta < -0.015,
            !player.onGround() && delta.y > 0.0,
            player.isCrouching,
            player.isSprinting
        )
    }

    private fun resetAllBotInputs() {
        for (bot in BotManager.bots) {
            bot.inputController.reset()
        }
    }

    private fun findPlayer(name: String): Player? {
        val level = ClientAccess.collectiveMinecraft.level ?: return null
        for (p in level.players()) {
            if (p.gameProfile.name.equals(name, ignoreCase = true)) {
                return p
            }
        }
        return null
    }
}
