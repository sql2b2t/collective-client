package me.collective.module.misc;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.manager.FriendManager;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Порт AntiBot из Meteor Client.
 *
 * <p>Убирает фейковых игроков: кого нет в таб-листе — чистит с клиента.
 * Чистит таргет аур от ботов.
 */
public class AntiBot extends Module {
    public final BooleanSetting tabList = add(new BooleanSetting("По таб-листу", true));
    public final BooleanSetting invisibles = add(new BooleanSetting("Невидимки", false));

    private long lastCheck = 0L;

    public AntiBot() {
        super("AntiBot", "Убирает ботов и фейковых игроков.", Category.MISC);
    }

    @Override
    public void onEnable() {
        lastCheck = System.currentTimeMillis();
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
        long now = System.currentTimeMillis();
        if (now - lastCheck < 2000) return;
        lastCheck = now;

        Set<UUID> tabIds = new HashSet<>();
        if (tabList.getValue()) {
            for (PlayerInfo entry : mc.getConnection().getOnlinePlayers()) {
                tabIds.add(entry.getProfile().id());
            }
        }

        for (Player p : new java.util.ArrayList<>(mc.level.players())) {
            // Player-list packets can arrive after entity spawn; never delete a fresh
            // real player or our explicit test dummy based on that transient absence.
            if (p == mc.player || !p.isAlive() || p.tickCount < 100 || FakePlayer.isLocalFake(p)) continue;
            if (FriendManager.getInstance().isFriend(p)) continue;

            if (tabList.getValue() && !tabIds.contains(p.getUUID())) {
                mc.level.removeEntity(p.getId(), Entity.RemovalReason.DISCARDED);
                continue;
            }
            if (invisibles.getValue() && p.isInvisible() && p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) {
                mc.level.removeEntity(p.getId(), Entity.RemovalReason.DISCARDED);
            }
        }
    }
}