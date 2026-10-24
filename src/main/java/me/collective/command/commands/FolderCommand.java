package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;

import java.io.File;

public class FolderCommand extends Command {
    public FolderCommand() {
        super("folder", "Opens client configuration directory.", ".folder", "openfolder");
    }

    @Override
    public void execute(String[] args) {
        File dir = new File(mc.runDirectory, "collective");
        if (!dir.exists()) dir.mkdirs();
        Util.getOperatingSystem().open(dir);
        ChatHelper.sendMessage(Component.literal("Opened configuration directory").styled(s -> s.withColor(ChatFormatting.GRAY)));
    }
}