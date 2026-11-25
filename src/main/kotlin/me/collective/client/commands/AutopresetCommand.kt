package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.ClientContext
import me.collective.client.settings.options.PresetCategory
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.inventory.Kit
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CompletableFuture

class AutopresetCommand : Command("autopreset") {

    init {
        setAliases("autoconfig", "autocfg")
    }

    private fun addPreset(context: CommandContext<*>): Int {
        val server = context.getArgument("server", String::class.java)
        val category = context.getArgument("category", PresetCategory::class.java)
        val preset = context.getArgument("preset", String::class.java)
        val normalizedServer = server.lowercase(Locale.ROOT)
        ClientContext.autoPresetRegistry.setPreset(normalizedServer, category, preset)
        return 1
    }

    private fun addKit(context: CommandContext<*>): Int {
        val server = context.getArgument("server", String::class.java)
        val kit = context.getArgument("value", Kit::class.java)
        val normalizedServer = server.lowercase(Locale.ROOT)
        ClientContext.autoPresetRegistry.setKit(normalizedServer, kit.name)
        return 1
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        var operationLiteral = literal<Any?>("add")

        val currentServerSuggestionsBuilder = argument("server", FancyStringArgument.fancyString()) as RequiredArgumentBuilder<Any?, *>
        var serverArgument = currentServerSuggestionsBuilder.suggests { context, suggestionsBuilder ->
            suggestCurrentServer(context, suggestionsBuilder)
        }

        val categoryArgument = argument("category", PresetCategoryArgument()) as RequiredArgumentBuilder<Any?, *>
        val addPresetActionBuilder = argument("preset", PresetArgumentType()) as RequiredArgumentBuilder<Any?, *>
        val presetAction = addPresetActionBuilder.executes { context -> addPreset(context) }
        val categoryBranch = categoryArgument.then(presetAction)
        serverArgument = serverArgument.then(categoryBranch) as RequiredArgumentBuilder<Any?, *>

        val kitLiteral = literal<Any?>("kit")
        val addKitActionBuilder = argument("value", KitArgumentType()) as RequiredArgumentBuilder<Any?, *>
        val kitAction = addKitActionBuilder.executes { context -> addKit(context) }
        val kitBranch = kitLiteral.then(kitAction)
        val addServerBranch = serverArgument.then(kitBranch)
        val addBranch = operationLiteral.then(addServerBranch)
        root.then(addBranch)

        operationLiteral = literal("delete")

        val configuredServerSuggestionsBuilder = argument("server", FancyStringArgument.fancyString()) as RequiredArgumentBuilder<Any?, *>
        serverArgument = configuredServerSuggestionsBuilder.suggests { context, suggestionsBuilder ->
            suggestConfiguredServers(context, suggestionsBuilder)
        }

        val removePresetActionBuilder = argument("category", PresetCategoryArgument()) as RequiredArgumentBuilder<Any?, *>
        val deleteCategoryAction = removePresetActionBuilder.executes { context -> removePreset(context) }
        serverArgument = serverArgument.then(deleteCategoryAction) as RequiredArgumentBuilder<Any?, *>

        val removeKitActionBuilder = literal<Any?>("kit")
        val deleteKitAction = removeKitActionBuilder.executes { context -> removeKit(context) }
        val removeServerActionBuilder = serverArgument.then(deleteKitAction) as RequiredArgumentBuilder<Any?, *>
        val deleteServerAction = removeServerActionBuilder.executes { context -> removeServer(context) }
        val deleteBranch = operationLiteral.then(deleteServerAction)
        root.then(deleteBranch)
    }

    private fun suggestCurrentServer(context: CommandContext<*>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val serverData = ClientContext.serverStateManager.serverData
        return if (serverData == null) {
            builder.buildFuture()
        } else {
            SharedSuggestionProvider.suggest(Collections.singleton(serverData.ip), builder)
        }
    }

    private fun removePreset(context: CommandContext<*>): Int {
        val server = context.getArgument("server", String::class.java)
        val category = context.getArgument("category", PresetCategory::class.java)
        val removed = ClientContext.autoPresetRegistry.removePreset(server, category)
        if (removed) {
            val successMessage = "Preset %s removed from auto-preset system".format(category.getName())
            ChatUtils.printChat(Component.literal(successMessage), ChatUtils.DEFAULT_SIGNATURE)
        } else {
            ChatUtils.printChat(Component.literal("Failed to remove preset from auto-preset system"), ChatUtils.DEFAULT_SIGNATURE)
        }
        return 1
    }

    private fun suggestConfiguredServers(context: CommandContext<*>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        @Suppress("UNCHECKED_CAST")
        val servers = (ClientContext.autoPresetRegistry.servers as Collection<String>)
        return SharedSuggestionProvider.suggest(servers, builder)
    }

    private fun removeKit(context: CommandContext<*>): Int {
        val server = context.getArgument("server", String::class.java)
        val normalizedServer = server.lowercase(Locale.ROOT)
        ClientContext.autoPresetRegistry.removeKit(normalizedServer)
        return 1
    }

    private fun removeServer(context: CommandContext<*>): Int {
        val server = context.getArgument("server", String::class.java)
        val removed = ClientContext.autoPresetRegistry.removeServer(server)
        if (removed) {
            val successMessage = "Server %s removed from auto-preset system".format(server)
            ChatUtils.printChat(Component.literal(successMessage), ChatUtils.DEFAULT_SIGNATURE)
        } else {
            ChatUtils.printChat(Component.literal("Failed to remove server from auto-preset system"), ChatUtils.DEFAULT_SIGNATURE)
        }
        return 1
    }
}
