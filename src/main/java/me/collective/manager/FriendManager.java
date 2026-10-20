package me.collective.manager;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class FriendManager {
    public static final FriendManager INSTANCE = new FriendManager();
    private final Set<String> friends = new HashSet<>();

    private FriendManager() {
    }

    private static String norm(String name) {
        return name == null ? null : name.toLowerCase(java.util.Locale.ROOT);
    }

    public static FriendManager getInstance() {
        return INSTANCE;
    }

    public boolean isFriend(String name) {
        return friends.contains(norm(name));
    }

    public boolean isFriend(net.minecraft.entity.player.PlayerEntity player) {
        if (player == null) return false;
        return isFriend(player.getName().getString());
    }

    public void addFriend(String name) {
        if (name != null) friends.add(norm(name));
    }

    public void removeFriend(String name) {
        friends.remove(norm(name));
    }

    public void clearFriends() {
        friends.clear();
    }

    public void setFriends(Collection<String> names) {
        friends.clear();
        if (names == null) return;
        for (String n : names) addFriend(n);
    }

    /** Защитная копия: внешний код не мутирует внутреннее множество. */
    public Set<String> getFriends() {
        return new HashSet<>(friends);
    }
}
