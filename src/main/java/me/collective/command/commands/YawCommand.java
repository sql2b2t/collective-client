package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class YawCommand extends Command {
    public YawCommand() {
        super("yaw", "Sets player camera yaw.", ".yaw <degrees>");
    }

    @Override
    public void execute(String[] args) {
        if (mc.player == null || args.length < 1) {
            ChatHelper.sendMessage(Component.literal("Usage: " + getSyntax()).styled(s -> s.withColor(ChatFormatting.RED)));
            return;
        }
        try {
            float y = Float.parseFloat(args[0]);
            mc.player.setYaw(y);
            ChatHelper.sendMessage(Component.literal("Yaw set to ").styled(s -> s.withColor(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(y)).styled(s -> s.withColor(ChatFormatting.WHITE))));
        } catch (Exception e) {
            ChatHelper.sendMessage(Component.literal("Invalid yaw number.").styled(s -> s.withColor(ChatFormatting.RED)));
        }
    }
}