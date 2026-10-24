package me.collective.command;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public abstract class Command {
    protected final Minecraft mc = Minecraft.getInstance();
    private final String name;
    private final String description;
    private final String syntax;
    private final String[] aliases;

    public Command(String name, String description, String syntax, String... aliases) {
        this.name = name;
        this.description = description;
        this.syntax = syntax;
        this.aliases = aliases;
    }

    public abstract void execute(String[] args);

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getSyntax() {
        return syntax;
    }

    public String[] getAliases() {
        return aliases;
    }

    public void sendMessage(String message) {
        ChatHelper.sendMessage(message);
    }
}