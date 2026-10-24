package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class DrawnCommand extends Command {
    public DrawnCommand() {
        super("drawn", "Toggles whether module is visible in ArrayList.", ".drawn <module>", "d");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1) {
            ChatHelper.sendMessage(Component.literal("Usage: " + getSyntax()).styled(s -> s.withColor(ChatFormatting.RED)));
            return;
        }
        Module mod = ModuleManager.INSTANCE.getModule(args[0]);
        if (mod == null) {
            ChatHelper.sendMessage(Component.literal("Module not found: " + args[0]).styled(s -> s.withColor(ChatFormatting.RED)));
            return;
        }
        ChatHelper.sendMessage(Component.literal("Module ").styled(s -> s.withColor(ChatFormatting.GRAY))
            .append(Component.literal(mod.getName()).styled(s -> s.withColor(ChatFormatting.WHITE)))
            .append(Component.literal(" drawn toggled").styled(s -> s.withColor(ChatFormatting.GRAY))));
    }
}