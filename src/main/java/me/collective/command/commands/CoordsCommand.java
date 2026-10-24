package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class CoordsCommand extends Command {
    public CoordsCommand() {
        super("coords", "Copies current coordinates to clipboard.", ".coords", "c", "coord", "pos");
    }

    @Override
    public void execute(String[] args) {
        if (mc.player == null) return;
        int x = mc.player.getBlockX();
        int y = mc.player.getBlockY();
        int z = mc.player.getBlockZ();
        String coords = x + " " + y + " " + z;
        mc.keyboard.setClipboard(coords);
        ChatHelper.sendMessage(Component.literal("Coordinates copied to clipboard: ").styled(s -> s.withColor(ChatFormatting.GRAY))
            .append(Component.literal(coords).styled(s -> s.withColor(ChatFormatting.GREEN))));
    }
}