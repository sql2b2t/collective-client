package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.config.ConfigManager;
import me.collective.manager.FriendManager;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.Set;

public class FriendCommand extends Command {
    public FriendCommand() {
        super("friend", "Manages your friends list.", ".friend <add|del|list|clear> [name]", "f");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1) {
            ChatHelper.sendMessage(Component.literal("Usage: " + getSyntax()).withStyle(s -> s.withColor(ChatFormatting.RED)));
            return;
        }

        String action = args[0].toLowerCase();
        FriendManager fm = FriendManager.getInstance();

        switch (action) {
            case "add" -> {
                if (args.length < 2) {
                    ChatHelper.sendMessage(Component.literal("Usage: .friend add <name>").withStyle(s -> s.withColor(ChatFormatting.RED)));
                    return;
                }
                String name = args[1];
                if (fm.isFriend(name)) {
                    ChatHelper.sendMessage(Component.literal(name + " is already in your friends list.").withStyle(s -> s.withColor(ChatFormatting.YELLOW)));
                    return;
                }
                fm.addFriend(name);
                ConfigManager.getInstance().requestSave();
                ChatHelper.sendMessage(Component.literal("Added ").withStyle(s -> s.withColor(ChatFormatting.GRAY))
                    .append(Component.literal(name).withStyle(s -> s.withColor(ChatFormatting.GREEN)))
                    .append(Component.literal(" to friends list.").withStyle(s -> s.withColor(ChatFormatting.GRAY))));
            }
            case "del", "remove" -> {
                if (args.length < 2) {
                    ChatHelper.sendMessage(Component.literal("Usage: .friend del <name>").withStyle(s -> s.withColor(ChatFormatting.RED)));
                    return;
                }
                String name = args[1];
                if (!fm.isFriend(name)) {
                    ChatHelper.sendMessage(Component.literal(name + " is not in your friends list.").withStyle(s -> s.withColor(ChatFormatting.RED)));
                    return;
                }
                fm.removeFriend(name);
                ConfigManager.getInstance().requestSave();
                ChatHelper.sendMessage(Component.literal("Removed ").withStyle(s -> s.withColor(ChatFormatting.GRAY))
                    .append(Component.literal(name).withStyle(s -> s.withColor(ChatFormatting.RED)))
                    .append(Component.literal(" from friends list.").withStyle(s -> s.withColor(ChatFormatting.GRAY))));
            }
            case "list" -> {
                Set<String> friends = fm.getFriends();
                if (friends.isEmpty()) {
                    ChatHelper.sendMessage(Component.literal("Your friends list is empty.").withStyle(s -> s.withColor(ChatFormatting.GRAY)));
                    return;
                }
                ChatHelper.sendMessage(Component.literal("Friends (" + friends.size() + "): ").withStyle(s -> s.withColor(ChatFormatting.GRAY))
                    .append(Component.literal(String.join(", ", friends)).withStyle(s -> s.withColor(ChatFormatting.GREEN))));
            }
            case "clear" -> {
                fm.clearFriends();
                ConfigManager.getInstance().requestSave();
                ChatHelper.sendMessage(Component.literal("Cleared all friends.").withStyle(s -> s.withColor(ChatFormatting.YELLOW)));
            }
            default -> ChatHelper.sendMessage(Component.literal("Unknown sub-command. Usage: " + getSyntax()).withStyle(s -> s.withColor(ChatFormatting.RED)));
        }
    }
}