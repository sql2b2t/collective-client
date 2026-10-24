package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.command.CommandManager;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class PrefixCommand extends Command {
    public PrefixCommand() {
        super("prefix", "Changes or shows the command prefix.", ".prefix [char]", "p");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1 || args[0].isBlank()) {
            ChatHelper.sendMessage(Component.literal("The current prefix is ").styled(s -> s.withColor(ChatFormatting.GRAY))
                .append(Component.literal(CommandManager.INSTANCE.getPrefix()).styled(s -> s.withColor(ChatFormatting.WHITE))));
            return;
        }

        String newPrefix = args[0].substring(0, 1);
        CommandManager.INSTANCE.setPrefix(newPrefix);
        ChatHelper.sendMessage(Component.literal("Prefix set to ").styled(s -> s.withColor(ChatFormatting.GRAY))
            .append(Component.literal(newPrefix).styled(s -> s.withColor(ChatFormatting.WHITE))));
    }
}