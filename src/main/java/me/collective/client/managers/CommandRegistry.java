package me.collective.client.managers;

import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.NamedFeature;
import me.collective.client.commands.BaritoneCommand;
import me.collective.client.commands.ModuleCommand;
import me.collective.client.commands.Command;
import me.collective.client.commands.EnemyFriendCommand;
import me.collective.client.events.CommandExecuteEvent;
import me.collective.client.events.EventHandler;
import me.collective.client.events.KeyboardHandlerCharTypedEvent;
import me.collective.client.events.bus.EventBus;
import me.collective.client.utils.KeyUtils;
import me.collective.client.modules.Module;
import me.collective.client.modules.client.SettingsModule;
import me.collective.client.settings.JsonConfigurable;
import me.collective.client.settings.NamedEntry;
import me.collective.client.settings.Setting;
import me.collective.client.settings.options.SocialRole;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;

public final class CommandRegistry extends Registry<Command> implements ClientAccess, JsonConfigurable {

   private static SettingsModule settingsModule;
   private static SettingsModule settingsModule() {
      if (settingsModule == null) {
         settingsModule = ClientContext.getModule(SettingsModule.class);
      }
      return settingsModule;
   }
   public static final SharedSuggestionProvider suggestionSource = new ClientSuggestionProvider(
      null, ClientAccess.collectiveMinecraft, ignored -> true
   );
   public static final CommandDispatcher commandDispatcher = new CommandDispatcher();
   private static String commandPrefix = ";";

   public static char getPrefixCharacter() {
      return commandPrefix.charAt(0);
   }

   private void registerRelationshipCommands() {
      this.registerCommand(new EnemyFriendCommand(SocialRole.FRIEND));
      this.registerCommand(new EnemyFriendCommand(SocialRole.ENEMY));
   }

   private void registerPathfindingCommand() {
      this.registerCommand(new BaritoneCommand());
   }

   public CommandRegistry() {
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
      this.registerCommand(new me.collective.client.commands.ActionCommand());
      this.registerCommand(new me.collective.client.commands.AliasCommand());
      this.registerCommand(new me.collective.client.commands.AlloffCommand());
      this.registerCommand(new me.collective.client.commands.AngleCommand());
      this.registerCommand(new me.collective.client.commands.AutopotCommand());
      this.registerCommand(new me.collective.client.commands.AutopresetCommand());
      this.registerCommand(new me.collective.client.commands.BindCommand());
      this.registerCommand(new me.collective.client.commands.BotCommand());
      this.registerCommand(new me.collective.client.commands.ChatfilterCommand());
      this.registerCommand(new me.collective.client.commands.CheststealerCommand());
      this.registerCommand(new me.collective.client.commands.ClipCommand());
      this.registerCommand(new me.collective.client.commands.ColorCommand());
      this.registerCommand(new me.collective.client.commands.CoordsCommand());
      this.registerCommand(new me.collective.client.commands.DefaultsCommand());
      this.registerCommand(new me.collective.client.commands.DisconnectCommand());
      this.registerCommand(new me.collective.client.commands.DrawnCommand());
      this.registerCommand(new me.collective.client.commands.FakeplayerCommand());
      this.registerCommand(new me.collective.client.commands.FireworksCommand());
      this.registerCommand(new me.collective.client.commands.FolderCommand());
      this.registerCommand(new me.collective.client.commands.GlintCommand());
      this.registerCommand(new me.collective.client.commands.GlobalCommand());
      this.registerCommand(new me.collective.client.commands.HelpCommand());
      this.registerCommand(new me.collective.client.commands.HighlightCommand());
      this.registerCommand(new me.collective.client.commands.KitCommand());
      this.registerCommand(new me.collective.client.commands.MacroCommand());
      this.registerCommand(new me.collective.client.commands.NamehistoryCommand());
      this.registerCommand(new me.collective.client.commands.NbtCommand());
      this.registerCommand(new me.collective.client.commands.PanicCommand());
      this.registerCommand(new me.collective.client.commands.PitchCommand());
      this.registerCommand(new me.collective.client.commands.PlaytimeCommand());
      this.registerCommand(new me.collective.client.commands.PluginsCommand());
      this.registerCommand(new me.collective.client.commands.PrefixCommand());
      this.registerCommand(new me.collective.client.commands.PresetCommand());
      this.registerCommand(new me.collective.client.commands.PrintCommand());
      this.registerCommand(new me.collective.client.commands.QueueCommand());
      this.registerCommand(new me.collective.client.commands.ReloadCommand());
      this.registerCommand(new me.collective.client.commands.SayCommand());
      this.registerCommand(new me.collective.client.commands.SeenCommand());
      this.registerCommand(new me.collective.client.commands.SessionCommand());
      this.registerCommand(new me.collective.client.commands.ToggleCommand());
      this.registerCommand(new me.collective.client.commands.SorterCommand());
      this.registerCommand(new me.collective.client.commands.MoverCommand());
      this.registerCommand(new me.collective.client.commands.TradeCommand());
      this.registerCommand(new me.collective.client.commands.WaitCommand());
      this.registerCommand(new me.collective.client.commands.WaypointsCommand());
      this.registerCommand(new me.collective.client.commands.WebhookCommand());
      this.registerCommand(new me.collective.client.commands.YawCommand());

      this.registerPathfindingCommand();
      this.registerRelationshipCommands();
      this.registerCommand(new me.collective.client.commands.IrcCommand());
      this.registerCommand(new me.collective.client.commands.AddonCommand());
      this.registerCommand(new me.collective.client.commands.ScriptCommand());
      this.registerModuleAliasCommands();
      ((List<Command>)this.getRegistry()).sort(Comparator.comparing((Function<Command, String>)NamedFeature::getName));
   }

   @Override
   public boolean register(Object entry) {
      Command command = (Command)entry;
      return this.registerCommand(command);
   }

   public static boolean executeCommand(String input) {
      CommandExecuteEvent event = new CommandExecuteEvent(input);
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.post(event);
      if (event.isCancelled()) {
         // Перехвачено (например WaitCommand) — команда выполнится позже.
         return true;
      }
      try {
         CommandDispatcher executionDispatcher = commandDispatcher;
         SharedSuggestionProvider source = suggestionSource;
         ParseResults parsed = commandDispatcher.parse(input, source);
         executionDispatcher.execute(parsed);
         return true;
      } catch (CommandSyntaxException ignoredFailure) {
         // Вызывающий код (макросы) решает сам, показывать ли ошибку.
         return false;
      } catch (Exception commandFailure) {
         // RuntimeException из аргументов/исполнителей не должен ронять клиентский поток.
         commandFailure.printStackTrace();
         return false;
      }
   }

   @Override
   public void fromJson(JsonElement config) {
      JsonObject objectForPresence = config.getAsJsonObject();
      if (objectForPresence.has("prefix")) {
         JsonObject objectForValue = config.getAsJsonObject();
         JsonElement prefixElement = objectForValue.get("prefix");
         String prefix = prefixElement.getAsString();

         setPrefix(prefix);
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject config = new JsonObject();
      String prefix = commandPrefix;
      config.addProperty("prefix", prefix);
      return config;
   }

   @Override
   public String getConfigName() {
      return "commands.json";
   }

   @Override
   public boolean registerEntry(NamedEntry entry) {
      Command command = (Command)entry;
      return this.registerCommand(command);
   }

   @EventHandler
   public void onKeyboardHandlerCharTypedEvent(KeyboardHandlerCharTypedEvent inputEvent) {
      if (KeyUtils.isShiftDown()) {
         return;
      }
      String prefixForLength = getPrefix();
      if (prefixForLength.length() != 1) {
         return;
      }
      String prefixForCharacter = getPrefix();
      char prefixCharacter = prefixForCharacter.charAt(0);
      if (prefixCharacter != inputEvent.getCharacter()) {
         return;
      }
      Minecraft minecraft = ClientAccess.collectiveMinecraft;
      ChatScreen chatScreen = new ChatScreen("", false);
      minecraft.setScreen(chatScreen);
   }

   private Optional<LiteralArgumentBuilder> createMatchingModuleCommand(Command command) {
      return ClientContext.moduleRegistry.findFirst((Predicate<Module>)module -> {
         String moduleName = module.getName();
         String commandName = command.getName();
         return moduleName.equalsIgnoreCase(commandName);
      }).map((Function<Module, LiteralArgumentBuilder>)module -> {
         LiteralArgumentBuilder moduleBuilder = LiteralArgumentBuilder.literal("module");
         ModuleCommand moduleCommand = new ModuleCommand(module);
         moduleCommand.exec(moduleBuilder);
         return moduleBuilder;
      });
   }

   public static void setPrefix(String prefix) {
      if (prefix.isBlank()) {
         return;
      }
      String updatedPrefix = prefix.substring(0, 1);
      commandPrefix = updatedPrefix;
      Setting<String> prefixSetting = settingsModule().prefixSetting;
      prefixSetting.setValue(updatedPrefix);
   }

   public boolean registerCommand(Command command) {
      String commandName = command.getName();
      LiteralArgumentBuilder builder = LiteralArgumentBuilder.literal(commandName);
      command.exec(builder);
      Optional<LiteralArgumentBuilder> moduleCommand = this.createMatchingModuleCommand(command);
      moduleCommand.ifPresent(builder::then);
      LiteralCommandNode canonicalNode = commandDispatcher.register(builder);

      for (String alias : command.getAliases()) {
         CommandAliasBuilder aliasBuilder = new CommandAliasBuilder(alias);
         Iterator children = canonicalNode.getChildren().iterator();
         while (children.hasNext()) {
            CommandNode child = (CommandNode)children.next();
            aliasBuilder.then(child);
         }
         aliasBuilder.executes(canonicalNode.getCommand());
         commandDispatcher.register(aliasBuilder);
      }
      return this.entries.add(command);
   }

   public static String getPrefix() {
      return commandPrefix;
   }

   private void registerModuleAliasCommands() {
      Iterator<Module> moduleIterator = ClientContext.moduleRegistry.getRegistry().iterator();
      while (moduleIterator.hasNext()) {
         Module module = moduleIterator.next();
         Optional<Command> matchingCommand = this.findFirst((Predicate<Command>)command -> {
            String commandName = command.getName();
            String moduleName = module.getName();
            return commandName.equalsIgnoreCase(moduleName);
         });
         if (matchingCommand.isPresent()) {
            continue;
         }
         for (String alias : module.getAliases()) {
            ModuleAliasBuilder aliasBuilder = new ModuleAliasBuilder(alias.toLowerCase(Locale.ROOT));
            ModuleCommand moduleCommand = new ModuleCommand(module);
            moduleCommand.exec(aliasBuilder);
            commandDispatcher.register(aliasBuilder);
         }
      }
   }

}
