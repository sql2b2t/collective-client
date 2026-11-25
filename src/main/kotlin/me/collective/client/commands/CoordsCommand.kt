package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.network.chat.Component
import kotlin.random.Random

class CoordsCommand : Command("coords") {

    private fun formatCoordinates(): String {
        val player = ClientAccess.collectiveMinecraft.player ?: return ""
        val x = player.x.toInt()
        val y = player.y.toInt()
        val z = player.z.toInt()

        val dimension = WorldUtils.getDimension()
        val dimensionName = dimension.displayName
        val randomSuffix = (Random.nextDouble() * 10000.0).toInt()
        return "X: $x, Y: $y, Z: $z in The $dimensionName [$randomSuffix]"
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val nameBuilder = argument("name", PlayerNameArgumentType()) as RequiredArgumentBuilder<Any?, *>
        val whisperAction = { context: com.mojang.brigadier.context.CommandContext<Any?> ->
            val playerName = context.getArgument("name", String::class.java)
            val coordinates = formatCoordinates()
            whisperCoordinates(playerName, coordinates)
            1
        }
        val nameArgument = nameBuilder.executes(whisperAction)
        val clipboardRoot = root.then(nameArgument) as LiteralArgumentBuilder<Any?>

        val clipboardAction = { _: com.mojang.brigadier.context.CommandContext<Any?> ->
            try {
                val keyboard = ClientAccess.collectiveMinecraft.keyboardHandler
                val coordinates = formatCoordinates()
                keyboard.clipboard = coordinates

                val confirmation = Component.literal("Your position has been copied to the clipboard.")
                ChatUtils.printChat(confirmation, ChatUtils.createSignature(-1))
            } catch (_: Exception) {
            }
            1
        }
        clipboardRoot.executes(clipboardAction)
    }

    private fun whisperCoordinates(recipient: String, coordinates: String) {
        val whisperCommand = "w $recipient $coordinates"
        val connection = ClientAccess.collectiveMinecraft.player?.connection
        connection?.sendCommand(whisperCommand)
    }
}
