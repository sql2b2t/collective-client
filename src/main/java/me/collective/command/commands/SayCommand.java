package me.collective.command.commands;

import me.collective.command.Command;

public class SayCommand extends Command {
    public SayCommand() {
        super("say", "Sends a chat message to server even if starting with prefix.", ".say <message>");
    }

    @Override
    public void execute(String[] args) {
        if (mc.getConnection() == null || args.length < 1) return;
        String msg = String.join(" ", args);
        mc.getConnection().sendChat(msg);
    }
}