package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.command.CommandManager;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;

public class HelpCommand extends Command {
    public HelpCommand() {
        super("help", "Displays list of available commands.", ".help", "h", "commands");
    }

    @Override
    public void execute(String[] args) {
        List<String> list = new ArrayList<>();
        for (Command cmd : CommandManager.INSTANCE.getCommands()) {
            list.add(cmd.getName());
        }

        MutableText msg = Component.literal("Commands (%d): ".formatted(list.size())).styled(s -> s.withColor(ChatFormatting.GRAY))
            .append(Component.literal(String.join(", ", list)).styled(s -> s.withColor(ChatHelper.getPrimaryTextColor())));

        ChatHelper.sendMessage(msg);
    }
}