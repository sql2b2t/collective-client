package me.collective.client.bots.control

import me.collective.client.bots.BotClient
import me.collective.client.bots.world.BotClientLevel
import me.collective.client.bots.world.BotLocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

class BotInteractionController(val bot: BotClient) {
    var sequence: Int = 0
    private var lastSentCarriedIndex: Int = -1
    var destroyBlockPos: BlockPos? = null
        private set
    var destroyDirection: Direction? = null
        private set
    var destroyProgress: Float = 0.0f
        private set
    var destroyDelay: Int = 0
    var isDestroying: Boolean = false
        private set

    fun tick() {
        if (destroyDelay > 0) {
            destroyDelay--
        }
        syncHeldItem()
    }

    fun syncHeldItem() {
        val selected = bot.playerState.selectedSlot
        if (selected != lastSentCarriedIndex) {
            lastSentCarriedIndex = selected
            bot.sendPacket(ServerboundSetCarriedItemPacket(selected))
        }
    }

    fun setSelectedSlot(slot: Int) {
        if (slot in 0..8) {
            bot.playerState.selectedSlot = slot
            syncHeldItem()
        }
    }

    fun startFallFlying() {
        val player = bot.worldState.botLocalPlayer ?: return
        bot.sendPacket(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING))
    }

    fun swapOffhand() {
        bot.sendPacket(
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ZERO,
                Direction.DOWN,
                nextSequence()
            )
        )
    }

    fun dropItem(dropAll: Boolean) {
        val action = if (dropAll) {
            ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
        } else {
            ServerboundPlayerActionPacket.Action.DROP_ITEM
        }
        bot.sendPacket(ServerboundPlayerActionPacket(action, BlockPos.ZERO, Direction.DOWN, nextSequence()))
    }

    fun swing(hand: InteractionHand) {
        bot.sendPacket(ServerboundSwingPacket(hand))
    }

    fun useItem(hand: InteractionHand) {
        syncHeldItem()
        bot.sendPacket(ServerboundUseItemPacket(hand, nextSequence(), bot.playerState.yRot, bot.playerState.xRot))
    }

    fun useItemOn(hitResult: BlockHitResult, hand: InteractionHand) {
        syncHeldItem()
        bot.sendPacket(ServerboundUseItemOnPacket(hand, hitResult, nextSequence()))
    }

    fun releaseUsingItem() {
        bot.sendPacket(
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO,
                Direction.DOWN,
                nextSequence()
            )
        )
    }

    fun interact(entity: Entity?, hand: InteractionHand) {
        if (entity == null) return
        syncHeldItem()
        val isShift = bot.inputController.sneak
        bot.sendPacket(ServerboundInteractPacket(entity.id, hand, Vec3.ZERO, isShift))
    }

    fun interactAt(entity: Entity?, hand: InteractionHand, hitPos: Vec3) {
        if (entity == null) return
        syncHeldItem()
        val isShift = bot.inputController.sneak
        val offset = hitPos.subtract(entity.x, entity.y, entity.z)
        bot.sendPacket(ServerboundInteractPacket(entity.id, hand, offset, isShift))
    }

    fun attack(entity: Entity?) {
        if (entity == null) return
        syncHeldItem()
        bot.sendPacket(ServerboundAttackPacket(entity.id))
        swing(InteractionHand.MAIN_HAND)
        val player = bot.worldState.botLocalPlayer
        if (player != null) {
            player.resetAttackStrengthTicker()
            player.armSprintResync()
        }
    }

    fun clickBlock(pos: BlockPos, direction: Direction): Boolean {
        if (destroyDelay > 0) return false
        val player = bot.worldState.botLocalPlayer ?: return false
        val immutablePos = pos.immutable()
        syncHeldItem()

        if (bot.playerState.gameType == GameType.CREATIVE) {
            bot.sendPacket(
                ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    immutablePos,
                    direction,
                    predictBreak(player, immutablePos)
                )
            )
            destroyDelay = 5
            return true
        }

        if (isDestroying && immutablePos == destroyBlockPos) {
            return continueDestroyBlock(immutablePos, direction)
        }

        if (isDestroying && destroyBlockPos != null) {
            bot.sendPacket(
                ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    destroyBlockPos!!,
                    Direction.DOWN,
                    0
                )
            )
        }

        val level = player.level()
        val state = level.getBlockState(immutablePos)
        if (state.isAir) {
            isDestroying = false
            return false
        }

        val instant = state.getDestroyProgress(player, level, immutablePos) >= 1.0f
        val seq = if (instant) predictBreak(player, immutablePos) else nextSequence()
        bot.sendPacket(
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                immutablePos,
                direction,
                seq
            )
        )
        destroyBlockPos = immutablePos
        destroyDirection = direction
        destroyProgress = 0.0f
        if (instant) {
            isDestroying = false
            destroyDelay = 5
        } else {
            isDestroying = true
        }
        return true
    }

    fun continueDestroyBlock(pos: BlockPos, direction: Direction): Boolean {
        val player = bot.worldState.botLocalPlayer ?: return false
        val immutablePos = pos.immutable()
        if (destroyDelay > 0) return false

        if (bot.playerState.gameType == GameType.CREATIVE) {
            return clickBlock(immutablePos, direction)
        }

        if (isDestroying && immutablePos == destroyBlockPos) {
            val level = player.level()
            val state = level.getBlockState(immutablePos)
            if (state.isAir) {
                isDestroying = false
                return false
            }

            syncHeldItem()
            destroyProgress += state.getDestroyProgress(player, level, immutablePos)
            if (destroyProgress >= 1.0f) {
                stopDestroyBlock(immutablePos, direction)
            }
            return true
        } else {
            return clickBlock(immutablePos, direction)
        }
    }

    fun stopDestroyBlock(pos: BlockPos, direction: Direction) {
        val player = bot.worldState.botLocalPlayer
        val seq = if (player != null) predictBreak(player, pos) else nextSequence()
        bot.sendPacket(
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                pos,
                direction,
                seq
            )
        )
        isDestroying = false
        destroyProgress = 0.0f
        destroyDelay = 5
    }

    fun abortDestroyBlock() {
        if (isDestroying) {
            val pos = destroyBlockPos
            if (pos != null) {
                bot.sendPacket(
                    ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                        pos,
                        Direction.DOWN,
                        0
                    )
                )
            }
            isDestroying = false
            destroyProgress = 0.0f
        }
    }

    fun reset() {
        isDestroying = false
        destroyBlockPos = null
        destroyDirection = null
        destroyProgress = 0.0f
        destroyDelay = 0
        lastSentCarriedIndex = -1
    }

    private fun predictBreak(player: BotLocalPlayer, pos: BlockPos): Int {
        val level = player.level() as? BotClientLevel ?: return nextSequence()
        sequence = level.predictBlockRemoval(pos, player, sequence)
        return sequence
    }

    private fun nextSequence(): Int = ++sequence
}
