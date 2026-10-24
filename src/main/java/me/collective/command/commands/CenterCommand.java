package me.collective.command.commands;

import me.collective.command.Command;

public class CenterCommand extends Command {
    public CenterCommand() {
        super("center", "Centers yourself on a block.", ".center");
    }

    @Override
    public void execute(String[] args) {
        if (mc.player == null) return;
        mc.player.setPos(
            Math.floor(mc.player.getX()) + 0.5,
            mc.player.getY(),
            Math.floor(mc.player.getZ()) + 0.5);
    }
}