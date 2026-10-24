package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class ToggleCommand extends Command {
    public ToggleCommand() {
        super("toggle", "Toggles a module on or off.", ".toggle <module> [state]", "t");
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

        if (args.length >= 2) {
            boolean state = Boolean.parseBoolean(args[1]);
            mod.setToggled(state);
        } else {
            mod.toggle();
        }

        MutableText msg = Component.literal("Module ").styled(s -> s.withColor(ChatFormatting.GRAY))
            .append(Component.literal(mod.getName()).styled(s -> s.withColor(ChatHelper.getPrimaryTextColor())))
            .append(Component.literal(" is now ").styled(s -> s.withColor(ChatFormatting.GRAY)))
            .append(Component.literal(mod.isToggled() ? "enabled" : "disabled").styled(s -> s.withColor(mod.isToggled() ? ChatFormatting.GREEN : ChatFormatting.RED)));

        ChatHelper.sendMessage(msg);
    }
}