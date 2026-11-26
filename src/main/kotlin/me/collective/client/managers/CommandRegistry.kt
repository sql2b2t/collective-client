package me.collective.client.managers

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.exceptions.CommandSyntaxException
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.NamedFeature
import me.collective.client.commands.BaritoneCommand
import me.collective.client.commands.Command
import me.collective.client.commands.EnemyFriendCommand
import me.collective.client.commands.ModuleCommand
import me.collective.client.events.CommandExecuteEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.KeyboardHandlerCharTypedEvent
import me.collective.client.modules.Module
import me.collective.client.modules.client.SettingsModule
import me.collective.client.settings.JsonConfigurable
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.options.SocialRole
import me.collective.client.utils.KeyUtils
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.commands.SharedSuggestionProvider
import java.util.Locale
import java.util.Optional
import java.util.function.Predicate

class CommandRegistry : Registry<Command>(), ClientAccess, JsonConfigurable {

    init {
        ClientAccess.eventBus.subscribe(this)
        registerCommand(me.collective.client.commands.ActionCommand())
        registerCommand(me.collective.client.commands.AliasCommand())
        registerCommand(me.collective.client.commands.AlloffCommand())
        registerCommand(me.collective.client.commands.AngleCommand())
        registerCommand(me.collective.client.commands.AutopotCommand())
        registerCommand(me.collective.client.commands.AutopresetCommand())
        registerCommand(me.collective.client.commands.BindCommand())
        registerCommand(me.collective.client.commands.BotCommand())
        registerCommand(me.collective.client.commands.ChatfilterCommand())
        registerCommand(me.collective.client.commands.CheststealerCommand())
        registerCommand(me.collective.client.commands.ClipCommand())
        registerCommand(me.collective.client.commands.ColorCommand())
        registerCommand(me.collective.client.commands.CoordsCommand())
        registerCommand(me.collective.client.commands.DefaultsCommand())
        registerCommand(me.collective.client.commands.DisconnectCommand())
        registerCommand(me.collective.client.commands.DrawnCommand())
        registerCommand(me.collective.client.commands.FakeplayerCommand())
        registerCommand(me.collective.client.commands.FireworksCommand())
        registerCommand(me.collective.client.commands.FolderCommand())
        registerCommand(me.collective.client.commands.GlintCommand())
        registerCommand(me.collective.client.commands.GlobalCommand())
        registerCommand(me.collective.client.commands.HelpCommand())
        registerCommand(me.collective.client.commands.HighlightCommand())
        registerCommand(me.collective.client.commands.KitCommand())
        registerCommand(me.collective.client.commands.MacroCommand())
        registerCommand(me.collective.client.commands.NamehistoryCommand())
        registerCommand(me.collective.client.commands.NbtCommand())
        registerCommand(me.collective.client.commands.PanicCommand())
        registerCommand(me.collective.client.commands.PitchCommand())
        registerCommand(me.collective.client.commands.PlaytimeCommand())
        registerCommand(me.collective.client.commands.PluginsCommand())
        registerCommand(me.collective.client.commands.PrefixCommand())
        registerCommand(me.collective.client.commands.PresetCommand())
        registerCommand(me.collective.client.commands.PrintCommand())
        registerCommand(me.collective.client.commands.QueueCommand())
        registerCommand(me.collective.client.commands.ReloadCommand())
        registerCommand(me.collective.client.commands.SayCommand())
        registerCommand(me.collective.client.commands.SeenCommand())
        registerCommand(me.collective.client.commands.SessionCommand())
        registerCommand(me.collective.client.commands.ToggleCommand())
        registerCommand(me.collective.client.commands.SorterCommand())
        registerCommand(me.collective.client.commands.MoverCommand())
        registerCommand(me.collective.client.commands.TradeCommand())
        registerCommand(me.collective.client.commands.WaitCommand())
        registerCommand(me.collective.client.commands.WaypointsCommand())
        registerCommand(me.collective.client.commands.WebhookCommand())
        registerCommand(me.collective.client.commands.YawCommand())

        registerPathfindingCommand()
        registerRelationshipCommands()
        registerCommand(me.collective.client.commands.IrcCommand())
        registerCommand(me.collective.client.commands.AddonCommand())
        registerCommand(me.collective.client.commands.ScriptCommand())
        registerModuleAliasCommands()
        (getRegistry() as MutableList<Command>).sortWith(Comparator.comparing(NamedFeature::getName))
    }

    private fun registerRelationshipCommands() {
        registerCommand(EnemyFriendCommand(SocialRole.FRIEND))
        registerCommand(EnemyFriendCommand(SocialRole.ENEMY))
    }

    private fun registerPathfindingCommand() {
        registerCommand(BaritoneCommand())
    }

    override fun register(entry: Any): Boolean {
        return registerCommand(entry as Command)
    }

    override fun fromJson(config: JsonElement) {
        val objectForPresence = config.asJsonObject
        if (objectForPresence.has("prefix")) {
            val prefix = objectForPresence.get("prefix").asString
            setPrefix(prefix)
        }
    }

    override fun toJson(): JsonElement {
        val config = JsonObject()
        config.addProperty("prefix", commandPrefix)
        return config
    }

    override fun getConfigName(): String = "commands.json"

    override fun registerEntry(entry: NamedEntry?): Boolean {
        return registerCommand(entry as Command)
    }

    @EventHandler
    fun onKeyboardHandlerCharTypedEvent(inputEvent: KeyboardHandlerCharTypedEvent) {
        if (KeyUtils.isShiftDown()) {
            return
        }
        val prefix = getPrefix()
        if (prefix.length != 1) {
            return
        }
        if (prefix[0] != inputEvent.character) {
            return
        }
        ClientAccess.collectiveMinecraft.gui.setScreen(ChatScreen("", false))
    }

    private fun createMatchingModuleCommand(command: Command): Optional<LiteralArgumentBuilder<Any?>> {
        return ClientContext.moduleRegistry.findFirst(Predicate { module ->
            module.getName().equals(command.getName(), ignoreCase = true)
        }).map { module ->
            val moduleBuilder = LiteralArgumentBuilder.literal<Any?>("module")
            val moduleCommand = ModuleCommand(module)
            moduleCommand.exec(moduleBuilder)
            moduleBuilder
        }
    }

    fun registerCommand(command: Command): Boolean {
        val builder = LiteralArgumentBuilder.literal<Any?>(command.getName())
        command.exec(builder)
        val moduleCommand = createMatchingModuleCommand(command)
        moduleCommand.ifPresent { builder.then(it) }
        val canonicalNode = commandDispatcher.register(builder)

        for (alias in command.getAliases()) {
            @Suppress("UNCHECKED_CAST")
            val aliasBuilder = CommandAliasBuilder(alias) as LiteralArgumentBuilder<Any?>
            for (child in canonicalNode.children) {
                aliasBuilder.then(child)
            }
            aliasBuilder.executes(canonicalNode.command)
            commandDispatcher.register(aliasBuilder)
        }
        return entries.add(command)
    }

    private fun registerModuleAliasCommands() {
        for (module in ClientContext.moduleRegistry.getRegistry()) {
            val matchingCommand = findFirst(Predicate { command ->
                command.getName().equals(module.getName(), ignoreCase = true)
            })
            if (matchingCommand.isPresent) {
                continue
            }
            for (alias in module.getAliases()) {
                @Suppress("UNCHECKED_CAST")
                val aliasBuilder = ModuleAliasBuilder(alias.lowercase(Locale.ROOT)) as LiteralArgumentBuilder<Any?>
                val moduleCommand = ModuleCommand(module)
                moduleCommand.exec(aliasBuilder)
                commandDispatcher.register(aliasBuilder)
            }
        }
    }

    companion object {
        private var settingsModule: SettingsModule? = null

        private fun settingsModule(): SettingsModule {
            if (settingsModule == null) {
                settingsModule = ClientContext.getModule(SettingsModule::class.java)
            }
            return settingsModule!!
        }

        @JvmField
        val suggestionSource: SharedSuggestionProvider = ClientSuggestionProvider::class.java
            .constructors
            .first { it.parameterCount == 3 }
            .newInstance(
                null,
                ClientAccess.collectiveMinecraft,
                net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS
            ) as SharedSuggestionProvider

        @JvmField
        val commandDispatcher: CommandDispatcher<Any?> = CommandDispatcher()

        private var commandPrefix: String = "."

        @JvmStatic
        fun getPrefixCharacter(): Char = commandPrefix[0]

        @JvmStatic
        fun executeCommand(input: String): Boolean {
            val event = CommandExecuteEvent(input)
            ClientAccess.eventBus.post(event)
            if (event.isCancelled()) {
                // Перехвачено (например WaitCommand) — команда выполнится позже.
                return true
            }
            return try {
                val parsed = commandDispatcher.parse(input, suggestionSource)
                commandDispatcher.execute(parsed)
                true
            } catch (_: CommandSyntaxException) {
                // Вызывающий код (макросы) решает сам, показывать ли ошибку.
                false
            } catch (commandFailure: Exception) {
                // RuntimeException из аргументов/исполнителей не должен ронять клиентский поток.
                commandFailure.printStackTrace()
                false
            }
        }

        @JvmStatic
        fun setPrefix(prefix: String) {
            if (prefix.isBlank()) {
                return
            }
            val updatedPrefix = prefix.substring(0, 1)
            commandPrefix = updatedPrefix
            settingsModule().prefixSetting.setValue(updatedPrefix)
        }

        @JvmStatic
        fun getPrefix(): String = commandPrefix
    }
}
