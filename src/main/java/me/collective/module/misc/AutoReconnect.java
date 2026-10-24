package me.collective.module.misc;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.module.Module;
import me.collective.render.SoundUtil;
import me.collective.setting.BooleanSetting;
import me.collective.setting.IntegerSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.chat.Component;

/**
 * AutoReconnect: авто-переподключение к последнему серверу после кика.
 * Задержка между попытками с ростом (backoff), лимит попыток
 * (0 — бесконечно), захваченная причина не учитывается. AfkLogout
 * помечает свой выход пометкой, чтобы не возвращаться на сервер.
 */
public class AutoReconnect extends Module {
    public final NumberSetting delay = add(new NumberSetting("Delay", 5, 1, 30, 1));
    public final IntegerSetting attempts = add(new IntegerSetting("Attempts", 3, 0, 25));
    public final NumberSetting backoff = add(new NumberSetting("Backoff", 2, 0, 15, 1));
    public final BooleanSetting skipAfkLogout = add(new BooleanSetting("Skip AfkLogout", true));

    private net.minecraft.client.multiplayer.ServerData lastServer;
    private long nextAttemptAt;
    private int used;
    private boolean countsActive;
    private volatile String lastReason = "";

    /** Совместный флаг пометки; при изменении не заканчивает перенос. */
    private static volatile boolean afkMarked;

    public AutoReconnect() {
        super("AutoReconnect", "Автоматически переподключается к серверу после разрыва соединения.", Category.MISC);
    }

    /** Вызов при выходе по достижении места, чтобы не переподключаться. */
    public static void markAfkLogout() {
        afkMarked = true;
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    private void resetState() {
        used = 0;
        countsActive = false;
        nextAttemptAt = 0L;
        lastReason = "";
    }

    /** Called by the client lifecycle even while no world/player exists. */
    public void onClientTick() {
        if (!isToggled()) return;
        Minecraft client = mc;

        // одобряющее соединение в игре: запоминаем сервер и обнуляем счётчик
        if (client.player != null) {
            net.minecraft.client.multiplayer.ServerData current = client.getCurrentServer();
            if (current != null) {
                lastServer = current;
            } else if (client.getConnection() != null && client.getConnection().getConnection() != null) {
                var address = client.getConnection().getConnection().getRemoteAddress();
                if (address != null) {
                    String str = address.toString();
                    if (str.startsWith("/")) str = str.substring(1);
                    if (lastServer == null || !lastServer.ip.equals(str)) {
                        lastServer = new net.minecraft.client.multiplayer.ServerData("Direct Server", str, net.minecraft.client.multiplayer.ServerData.Type.OTHER);
                    }
                }
            }
            resetState();
            afkMarked = false;
            return;
        }

        if (lastServer == null) return;
        if (!(client.gui.screen() instanceof DisconnectedScreen)) {
            countsActive = false;
            return;
        }

        boolean afkByReason = lastReason.contains("[AfkLogout]");
        if (skipAfkLogout.getValue() && (afkMarked || afkByReason)) {
            // Keep suppression for the whole disconnect, not just its first tick.
            return;
        }

        long now = System.currentTimeMillis();
        if (!countsActive) {
            countsActive = true;
            used = 0;
            nextAttemptAt = now + Math.round(delay.getFloatValue() * 1000f);
        }
        if (now < nextAttemptAt) return;

        int limit = attempts.getIntValue();
        if (limit > 0 && used >= limit) {
            // лимит исчерпан — ждём пока игрок сам уйдёт с экрана кика
            return;
        }

        connect(client);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundDisconnectPacket packet) {
            Component reason = packet.reason();
            lastReason = reason == null ? "" : reason.getString();
        }
    }

    private void connect(Minecraft client) {
        net.minecraft.client.multiplayer.ServerData info = lastServer;
        if (info == null || info.ip == null || info.ip.isBlank()) return;
        used++;
        long step = Math.round((delay.getFloatValue() + backoff.getFloatValue() * used) * 1000f);
        nextAttemptAt = System.currentTimeMillis() + step;
        SoundUtil.playClick(0.15f, 1.1f);
        ConnectScreen.startConnecting(client.gui.screen(), client,
                ServerAddress.parseString(info.ip), info, false, null);
    }
}