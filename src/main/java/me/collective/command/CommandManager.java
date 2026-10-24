package me.collective.command;

import me.collective.command.commands.*;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.setting.*;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

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
        register(new IrcCommand());
        register(new FolderCommand());
        register(new DisconnectCommand());
        register(new DigLogCommand());
        register(new SorterCommand());
        register(new MoverCommand());
        register(new ElytraFlyCommand());
        register(new AddonCommand());
        register(new ScriptCommand());
        register(new McpCommand());
        register(new HudImageCommand());
        register(new ConfigCommand());
        register(new FriendCommand());
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
            boolean hit = cmd.getName().equalsIgnoreCase(name);
            if (!hit) {
                for (String alias : cmd.getAliases()) {
                    if (alias.equalsIgnoreCase(name)) {
                        hit = true;
                        break;
                    }
                }
            }
            if (!hit) continue;

            try {
                cmd.execute(args);
            } catch (Throwable t) {
                // Команда обязана никогда не ронять клиент: сваливаем в чат.
                t.printStackTrace();
                ChatHelper.sendMessage(Component.literal("Ошибка команды: " + t).styled(s -> s.withColor(ChatFormatting.RED)));
            }
            return true;
        }

        ChatHelper.sendMessage(Component.literal("Unknown command. Type " + prefix + "help for a list of commands.").styled(s -> s.withColor(ChatFormatting.RED)));
        return true;
    }
}