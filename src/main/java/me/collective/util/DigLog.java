package me.collective.util;

import me.collective.command.ChatHelper;
import me.collective.event.EventBus;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * Диагностика исходящих dig-пакетов: помечает, был ли Look-пакет отправлен
 * прямо перед копанием (в ванильном трафике такого не бывает). Включается
 * командой .diglog и подписывается на шину только на время работы.
 */
public final class DigLog {
    private static DigLog INSTANCE;

    private boolean enabled;
    private boolean lookSeenThisTick;

    private DigLog() {
    }

    public static DigLog getInstance() {
        if (INSTANCE == null) INSTANCE = new DigLog();
        return INSTANCE;
    }

    public static boolean toggle() {
        DigLog log = getInstance();
        if (log.enabled) {
            EventBus.INSTANCE.unsubscribe(log);
            log.enabled = false;
            log.lookSeenThisTick = false;
            return false;
        }

        EventBus.INSTANCE.subscribe(log);
        log.enabled = true;
        return true;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    @EventHandler
    public void onSend(PacketEvent.Send event) {
        if (!this.enabled) return;

        Packet<?> packet = event.packet;

        if (packet instanceof ServerboundMovePlayerPacket.Rot) {
            this.lookSeenThisTick = true;
            return;
        }

        if (packet instanceof net.minecraft.network.protocol.game.ServerboundPunchPacket) {
            ChatHelper.sendMessage(String.format(
                "[DigLog] SWING %s",
                this.lookSeenThisTick ? "§e(look-пакет был до свинга)" : "§f(без look)"));
            return;
        }

        if (packet instanceof ServerboundPlayerActionPacket action) {
            ServerboundPlayerActionPacket.Action type = action.getAction();
            if (type == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                || type == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK
                || type == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
                ChatHelper.sendMessage(String.format(
                    "[DigLog] %s %s dir=%s seq=%d %s",
                    type,
                    action.getPos().toShortString(),
                    action.getDirection(),
                    action.getSequence(),
                    this.lookSeenThisTick ? "§e(look-пакет прямо перед дигом)" : "§f(без look)"));
            }

            this.lookSeenThisTick = false;
            return;
        }

        if (packet instanceof net.minecraft.network.protocol.game.ServerboundClientTickEndPacket) {
            this.lookSeenThisTick = false;
        }
    }
}