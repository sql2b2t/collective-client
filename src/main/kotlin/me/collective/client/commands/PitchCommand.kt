package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.math.MathConstants
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

class PitchCommand : Command("pitch") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val argumentBuilder = (argument("pitch", RelativeFloatArgument.relativeFloatArg()) as RequiredArgumentBuilder<Any?, *>).executes { commandContext ->
            val argument = commandContext.getArgument("pitch", RelativeFloatArgument.RelativeFloat::class.java)
            var pitch = argument.value
            if (argument.relative) {
                val playerForPitch = ClientAccess.collectiveMinecraft.player
                if (playerForPitch != null) {
                    pitch += playerForPitch.xRot
                }
            }
            pitch = Mth.clamp(pitch, -MathConstants.NINETY_DEGREES.toFloat(), MathConstants.NINETY_DEGREES.toFloat())

            val player = ClientAccess.collectiveMinecraft.player
            player?.xRot = pitch

            val message = Component.literal("Player's pitch has been set to $pitch.")
            val signature = ChatUtils.createSignature(-1)
            ChatUtils.printChat(message, signature)
            1
        }
        root.then(argumentBuilder)
    }
}
