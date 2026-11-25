package me.collective.client.modules.client

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.movement.MovementSpeedTracker
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.settings.Setting
import me.collective.client.settings.options.AntiCheatRotationsChoice

import me.collective.mixin.ServerboundMovePlayerPacketAccessor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

class AntiCheatMovementHandler(antiCheatModule: AntiCheatModule) : ClientAccess {
    private val module: AntiCheatModule = antiCheatModule

    fun isBaritoneCompatibilityOverrideActive(): Boolean {
        return false
    }

    private fun shouldOffsetPacketYaw(packet: ServerboundMovePlayerPacket?): Boolean {
        if (!MovementPhysics.isLocalPlayerMoving()) {
            val speedTracker: MovementSpeedTracker = ClientContext.movementSpeedTracker
            val averageSpeed = speedTracker.averageSpeed
            if (averageSpeed < 5.0) {
                return false
            }
        }
        val twoBTwoTSetting = this.module.value2b2tSetting
        if (!twoBTwoTSetting.value) {
            return false
        }
        if (PlayerStateUtils.isFallFlying()) {
            return false
        }
        if (packet != null) {
            val player = ClientAccess.collectiveMinecraft.player!!
            val inventoryScreenWithPlayerMenu = ClientAccess.collectiveMinecraft.screen is AbstractContainerScreen<*>
                && player != null &&
         player.containerMenu == player.inventoryMenu
            if (!packet.hasRotation() && !inventoryScreenWithPlayerMenu) {
                return false
            }
        }
        return this.module.movementYawOffsetEnabled ||
         packet == null
    }

    fun onPacketSend(event: PacketEvent.Send) {
        val packet = event.packet
        if (packet is ServerboundMovePlayerPacket && this.shouldOffsetPacketYaw(packet)) {
            (packet as ServerboundMovePlayerPacketAccessor).setYaw(packet.getYRot(0.0f) + AntiCheatModule.ROTATION_JITTER_STEP)
        }
    }

    fun prepareMovement() {
        if (this.shouldPrepareMovement()) {
            val rotationModeSetting = this.module.rotationsSetting
            if (rotationModeSetting.value == AntiCheatRotationsChoice.SILENT) {
                val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
                val playerX = player.x
                val playerY = player.y
                val playerZ = player.z
                val playerYaw = player.yRot
                val playerPitch = player.xRot
                val capturedOnGround = player.onGround()
                PacketSender.sendPositionRotation(
                    playerX, playerY, playerZ, playerYaw, playerPitch, capturedOnGround
                )
            }
        }
    }

    fun shouldPrepareMovement(): Boolean {
        return this.shouldOffsetPacketYaw(null)
    }
}
