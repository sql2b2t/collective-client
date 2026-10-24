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
            ChatHelper.sendMessage(Component.literal("Usage: " + getSyntax()).styled(s -> s.withColor(ChatFormatting.RED)));
            return;
        }
        try {
            float p = Float.parseFloat(args[0]);
            mc.player.setPitch(p);
            ChatHelper.sendMessage(Component.literal("Pitch set to ").styled(s -> s.withColor(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(p)).styled(s -> s.withColor(ChatFormatting.WHITE))));
        } catch (Exception e) {
            ChatHelper.sendMessage(Component.literal("Invalid pitch number.").styled(s -> s.withColor(ChatFormatting.RED)));
        }
    }
}