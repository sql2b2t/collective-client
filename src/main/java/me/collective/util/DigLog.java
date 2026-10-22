package me.collective.util;

import me.collective.command.ChatHelper;
import me.collective.event.EventBus;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

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

        if (packet instanceof PlayerMoveC2SPacket.LookAndOnGround) {
            this.lookSeenThisTick = true;
            return;
        }

        if (packet instanceof net.minecraft.network.packet.c2s.play.HandSwingC2SPacket) {
            ChatHelper.sendMessage(String.format(
                "[DigLog] SWING %s",
                this.lookSeenThisTick ? "§e(look-пакет был до свинга)" : "§f(без look)"));
            return;
        }

        if (packet instanceof PlayerActionC2SPacket action) {
            PlayerActionC2SPacket.Action type = action.getAction();
            if (type == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                || type == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK
                || type == PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) {
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

        if (packet instanceof ClientTickEndC2SPacket) {
            this.lookSeenThisTick = false;
        }
    }
}
