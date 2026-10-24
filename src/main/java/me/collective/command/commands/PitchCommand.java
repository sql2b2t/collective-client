package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class PitchCommand extends Command {
    public PitchCommand() {
        super("pitch", "Sets player camera pitch.", ".pitch <degrees>");
    }

    @Override
    public void execute(String[] args) {
        if (mc.player == null || args.length < 1) {
            ChatHelper.sendMessage(Component.literal("Usage: " + getSyntax()).withStyle(s -> s.withColor(ChatFormatting.RED)));
            return;
        }
        try {
            float p = Float.parseFloat(args[0]);
            mc.player.setXRot(p);
            ChatHelper.sendMessage(Component.literal("Pitch set to ").withStyle(s -> s.withColor(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(p)).withStyle(s -> s.withColor(ChatFormatting.WHITE))));
        } catch (Exception e) {
            ChatHelper.sendMessage(Component.literal("Invalid pitch number.").withStyle(s -> s.withColor(ChatFormatting.RED)));
        }
    }
}