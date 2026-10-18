package me.collective.command;

import me.collective.command.commands.*;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.setting.*;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandManager {
    public static final CommandManager INSTANCE = new CommandManager();

    private final List<Command> commands = new ArrayList<>();
    private String prefix = ".";

    public CommandManager() {
        register(new ToggleCommand());
        register(new BindCommand());
        register(new PrefixCommand());
        register(new HelpCommand());
        register(new CoordsCommand());
        register(new CenterCommand());        register(new PitchCommand());
        register(new YawCommand());
        register(new DrawnCommand());
        register(new SayCommand());
        register(new FolderCommand());
        register(new DisconnectCommand());
        register(new SorterCommand());
        register(new MoverCommand());
        register(new ElytraFlyCommand());
        register(new AddonCommand());
        register(new ScriptCommand());
        register(new McpCommand());
        register(new HudImageCommand());
        register(new ConfigCommand());
    }

    public void register(Command command) {
        commands.add(command);
    }

    public List<Command> getCommands() {
        return commands;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public boolean execute(String rawMessage) {
        if (!rawMessage.startsWith(prefix)) {
            return false;
        }

        String content = rawMessage.substring(prefix.length()).trim();
        if (content.isEmpty()) {
            return false;
        }

        String[] parts = content.split("\\s+");
        String name = parts[0];
        String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

        // 1. Check registered commands
        for (Command cmd : commands) {
            if (cmd.getName().equalsIgnoreCase(name)) {
                cmd.execute(args);
                return true;
            }
            for (String alias : cmd.getAliases()) {
                if (alias.equalsIgnoreCase(name)) {
                    cmd.execute(args);
                    return true;
                }
            }
        }

        ChatHelper.sendMessage(Text.literal("Unknown command. Type " + prefix + "help for a list of commands.").styled(s -> s.withColor(Formatting.RED)));
        return true;
    }
}
