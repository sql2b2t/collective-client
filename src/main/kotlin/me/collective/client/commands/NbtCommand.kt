package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.exceptions.CommandSyntaxException
import me.collective.client.ClientAccess
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.commands.arguments.NbtPathArgument.NbtPath
import net.minecraft.nbt.NbtUtils
import net.minecraft.network.chat.Component
import net.minecraft.server.commands.data.EntityDataAccessor

class NbtCommand : Command("nbt") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        root.executes {
            val player = ClientAccess.collectiveMinecraft.player ?: return@executes 0
            val accessor = EntityDataAccessor(player)
            val path = NbtPath.of("SelectedItem")
            val message = Component.empty().append("Nbt: ")

            try {
                val data = accessor.data
                val values = path.get(data)
                if (values.isNotEmpty()) {
                    val first = values.first()
                    val formatted = NbtUtils.toPrettyComponent(first)
                    message.append(formatted)
                }
            } catch (exception: CommandSyntaxException) {
                message.append("{}")
            }

            val signature = ChatUtils.createSignature(-1)
            ChatUtils.printChat(message, signature)
            1
        }
    }
}
