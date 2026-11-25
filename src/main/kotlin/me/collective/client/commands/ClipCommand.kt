package me.collective.client.commands

import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.network.PacketSender
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

class ClipCommand : Command("clip") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val verticalCommand = literal<Any?>("v")
        val verticalValue = argument("value", FloatArgumentType.floatArg()) as RequiredArgumentBuilder<Any?, *>
        val verticalAction = { context: com.mojang.brigadier.context.CommandContext<Any?> ->
            val argument = context.getArgument("value", java.lang.Float::class.java).toFloat()
            val distance = argument.coerceIn(-10000.0f, 10000.0f)
            sendAdditionalMovementPackets(distance, false)
            ChatUtils.printChat(
                Component.literal("Clipped ")
                    .append(formatText("%.1f".format(distance)))
                    .append(" blocks vertically."),
                ChatUtils.DEFAULT_SIGNATURE
            )
            val player = ClientAccess.collectiveMinecraft.player
            if (player != null) {
                if (player.isPassenger) {
                    val vehicle = player.vehicle
                    if (vehicle != null) {
                        val x = vehicle.x
                        val y = vehicle.y + distance
                        val z = vehicle.z
                        vehicle.setPos(x, y, z)
                    }
                } else {
                    val x = player.x
                    val y = player.y + distance
                    val z = player.z
                    player.setPos(x, y, z)
                    PacketSender.sendPosition(player.x, player.y, player.z, true)
                }
            }
            1
        }
        val verticalArguments = verticalValue.executes(verticalAction)
        val verticalSubcommand = verticalCommand.then(verticalArguments)
        val parent = root.then(verticalSubcommand) as LiteralArgumentBuilder<Any?>

        val horizontalCommand = literal<Any?>("h")
        val horizontalValue = argument("value", FloatArgumentType.floatArg()) as RequiredArgumentBuilder<Any?, *>
        val horizontalAction = { context: com.mojang.brigadier.context.CommandContext<Any?> ->
            val argument = context.getArgument("value", java.lang.Float::class.java).toFloat()
            val distance = argument.coerceIn(-30000000.0f, 30000000.0f)
            val player = ClientAccess.collectiveMinecraft.player
            if (player != null) {
                val cosineYaw = (player.yRot + MathConstants.NINETY_DEGREES).toDouble()
                val cosine = cos(Math.toRadians(cosineYaw))
                val sineYaw = (player.yRot + MathConstants.NINETY_DEGREES).toDouble()
                val sine = sin(Math.toRadians(sineYaw))
                sendAdditionalMovementPackets(distance, true)
                ChatUtils.printChat(
                    Component.literal("Clipped ")
                        .append(formatText("%.1f".format(distance)))
                        .append(" blocks horizontally."),
                    ChatUtils.DEFAULT_SIGNATURE
                )
                if (player.isPassenger) {
                    val vehicle = player.vehicle
                    if (vehicle != null) {
                        val x = vehicle.x + distance * cosine
                        val y = vehicle.y
                        val z = vehicle.z + distance * sine
                        vehicle.setPos(x, y, z)
                    }
                } else {
                    val x = player.x + distance * cosine
                    val y = player.y
                    val z = player.z + distance * sine
                    player.setPos(x, y, z)
                    PacketSender.sendPosition(player.x, player.y, player.z, true)
                }
            }
            1
        }
        val horizontalArguments = horizontalValue.executes(horizontalAction)
        val horizontalSubcommand = horizontalCommand.then(horizontalArguments)
        parent.then(horizontalSubcommand)
    }

    private fun sendAdditionalMovementPackets(distance: Float, horizontal: Boolean) {
        val step = if (horizontal) 9.0f else 10.0f
        val steps = distance / step
        var packetCount = ceil(abs(steps)).toInt()
        if (packetCount > 20) {
            packetCount = 1
        }

        for (packetIndex in 0 until packetCount - 1) {
            val player = ClientAccess.collectiveMinecraft.player ?: continue
            if (player.isPassenger) {
                val vehicle = player.vehicle ?: continue
                val packet: Packet<*> = ServerboundMoveVehiclePacket.fromEntity(vehicle)
                PacketSender.send(packet)
            } else {
                val packet: Packet<*> = StatusOnly(true, player.horizontalCollision)
                PacketSender.send(packet)
            }
        }
    }
}
