package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import kotlin.math.abs

class YawCommand : Command("yaw") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val argumentBuilder = (argument("yaw", RelativeFloatArgument.relativeFloatArg()) as RequiredArgumentBuilder<Any?, *>).executes { commandContext ->
            val argument = commandContext.getArgument("yaw", RelativeFloatArgument.RelativeFloat::class.java)
            var yaw = argument.value
            val player = ClientAccess.collectiveMinecraft.player
            if (argument.relative && player != null) {
                yaw += player.yRot
            }
            if (yaw.isNaN() && player != null) {
                yaw = player.yRot
            }
            val yawMagnitude = abs(yaw)
            if (yawMagnitude > 7200.0f) {
                yaw %= 360.0f
            }
            player?.yRot = yaw

            val suffix = if (yaw > 180.0f || yaw < -180.0f) " ($yaw)" else ""
            val wrappedYaw = Mth.wrapDegrees(yaw)
            val formattedMessage = "Player's yaw has been set to %.2f%s.".format(wrappedYaw, suffix)
            val message = Component.literal(formattedMessage)
            ChatUtils.printChat(message, -1)
            1
        }
        root.then(argumentBuilder)
    }
}
