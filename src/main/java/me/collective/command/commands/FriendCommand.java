package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.config.ConfigManager;
import me.collective.manager.FriendManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Set;

public class FriendCommand extends Command {
    public FriendCommand() {
        super("friend", "Manages your friends list.", ".friend <add|del|list|clear> [name]", "f");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1) {
            ChatHelper.sendMessage(Text.literal("Usage: " + getSyntax()).styled(s -> s.withColor(Formatting.RED)));
            return;
        }

        String action = args[0].toLowerCase();
        FriendManager fm = FriendManager.getInstance();

        switch (action) {
            case "add" -> {
                if (args.length < 2) {
                    ChatHelper.sendMessage(Text.literal("Usage: .friend add <name>").styled(s -> s.withColor(Formatting.RED)));
                    return;
                }
                String name = args[1];
                if (fm.isFriend(name)) {
                    ChatHelper.sendMessage(Text.literal(name + " is already in your friends list.").styled(s -> s.withColor(Formatting.YELLOW)));
                    return;
                }
                fm.addFriend(name);
                ConfigManager.getInstance().requestSave();
                ChatHelper.sendMessage(Text.literal("Added ").styled(s -> s.withColor(Formatting.GRAY))
                    .append(Text.literal(name).styled(s -> s.withColor(Formatting.GREEN)))
                    .append(Text.literal(" to friends list.").styled(s -> s.withColor(Formatting.GRAY))));
            }
            case "del", "remove" -> {
                if (args.length < 2) {
                    ChatHelper.sendMessage(Text.literal("Usage: .friend del <name>").styled(s -> s.withColor(Formatting.RED)));
                    return;
                }
                String name = args[1];
                if (!fm.isFriend(name)) {
                    ChatHelper.sendMessage(Text.literal(name + " is not in your friends list.").styled(s -> s.withColor(Formatting.RED)));
                    return;
                }
                fm.removeFriend(name);
                ConfigManager.getInstance().requestSave();
                ChatHelper.sendMessage(Text.literal("Removed ").styled(s -> s.withColor(Formatting.GRAY))
                    .append(Text.literal(name).styled(s -> s.withColor(Formatting.RED)))
                    .append(Text.literal(" from friends list.").styled(s -> s.withColor(Formatting.GRAY))));
            }
            case "list" -> {
                Set<String> friends = fm.getFriends();
                if (friends.isEmpty()) {
                    ChatHelper.sendMessage(Text.literal("Your friends list is empty.").styled(s -> s.withColor(Formatting.GRAY)));
                    return;
                }
                ChatHelper.sendMessage(Text.literal("Friends (" + friends.size() + "): ").styled(s -> s.withColor(Formatting.GRAY))
                    .append(Text.literal(String.join(", ", friends)).styled(s -> s.withColor(Formatting.GREEN))));
            }
            case "clear" -> {
                fm.clearFriends();
                ConfigManager.getInstance().requestSave();
                ChatHelper.sendMessage(Text.literal("Cleared all friends.").styled(s -> s.withColor(Formatting.YELLOW)));
            }
            default -> ChatHelper.sendMessage(Text.literal("Unknown sub-command. Usage: " + getSyntax()).styled(s -> s.withColor(Formatting.RED)));
        }
    }
}
