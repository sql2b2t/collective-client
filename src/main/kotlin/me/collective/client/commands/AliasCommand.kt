package me.collective.client.commands

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientContext
import me.collective.client.modules.Module
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component
import java.util.Locale

class AliasCommand : Command("alias") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>
        val moduleCommands = buildTargetCommands(AliasTarget.MODULE)
        val rootWithModuleCommands = root.then(moduleCommands) as LiteralArgumentBuilder<Any?>
        val playerCommands = buildTargetCommands(AliasTarget.PLAYER)
        rootWithModuleCommands.then(playerCommands)
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildTargetCommands(targetKind: AliasTarget): ArgumentBuilder<Any?, *> {
        val targetLiteral = targetKind.name.lowercase(Locale.ROOT)
        var targetCommands = literal<Any?>(targetLiteral)

        val setOperationCommands = literal<Any?>("set")
        val setTargetArgument = argument("target", targetKind.argumentType) as RequiredArgumentBuilder<Any?, *>

        val aliasBuilder = argument("alias", FancyStringArgument.fancyString()) as RequiredArgumentBuilder<Any?, *>
        val aliasArgument = aliasBuilder.executes { context ->
            val alias = context.getArgument("alias", String::class.java)
            val targetName: String
            if (targetKind == AliasTarget.MODULE) {
                val targetModule = context.getArgument("target", Module::class.java)
                ClientContext.aliasRegistry.setModuleAlias(targetModule, alias)
                targetName = targetModule.name
            } else {
                val targetPlayerName = context.getArgument("target", String::class.java)
                ClientContext.aliasRegistry.setPlayerAlias(targetPlayerName, alias)
                targetName = targetPlayerName
            }

            ChatUtils.printChat(
                Component.literal("Alias for ")
                    .append(formatText(targetName))
                    .append(" has been set to ")
                    .append(formatText(alias))
                    .append("."),
                ChatUtils.DEFAULT_SIGNATURE
            )
            1
        }
        val setTargetCommands = setTargetArgument.then(aliasArgument)
        val setCommands = setOperationCommands.then(setTargetCommands)
        targetCommands = targetCommands.then(setCommands) as LiteralArgumentBuilder<Any?>

        val removeOperationCommands = literal<Any?>("remove")
        val removeTargetArgument = argument("target", targetKind.argumentType) as RequiredArgumentBuilder<Any?, *>
        val removeExecutor = { context: com.mojang.brigadier.context.CommandContext<Any?> ->
            val targetName: String
            if (targetKind == AliasTarget.MODULE) {
                val targetModule = context.getArgument("target", Module::class.java)
                ClientContext.aliasRegistry.removeModuleAlias(targetModule)
                targetName = targetModule.name
            } else {
                val targetPlayerName = context.getArgument("target", String::class.java)
                ClientContext.aliasRegistry.removePlayerAlias(targetPlayerName)
                targetName = targetPlayerName
            }

            ChatUtils.printChat(
                Component.literal("Alias for ")
                    .append(formatText(targetName))
                    .append(" has been removed."),
                ChatUtils.DEFAULT_SIGNATURE
            )
            1
        }
        val removeTargetCommands = removeTargetArgument.executes(removeExecutor)
        val removeCommands = removeOperationCommands.then(removeTargetCommands)
        return targetCommands.then(removeCommands)
    }

    private enum class AliasTarget(val argumentType: ArgumentType<*>) {
        MODULE(ModuleArgumentType()),
        PLAYER(PlayerNameArgumentType())
    }
}
