package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

/**
 * Порт AntiHunger из Meteor Client.
 *
 * <p>Режет расход голода: подменяет спринт-пакеты, сервер думает что идёшь.
 * Для миллионных переходов по хайвеям.
 */
public class AntiHunger extends Module {
    public final BooleanSetting spoofSprint = add(new BooleanSetting("Подмена спринта", true));
    public final BooleanSetting spoofJump = add(new BooleanSetting("Подмена прыжка", false));
    public final BooleanSetting grimSafe = add(new BooleanSetting("GrimSafe", true));

    public AntiHunger() {
        super("AntiHunger", "Меньше расход голода при беге и прыжках.", Category.MOVEMENT);
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!(event.packet instanceof ServerboundPlayerCommandPacket cmd)) return;
        if (spoofSprint.getValue() && (cmd.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING
            || cmd.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING)) {
            if (grimSafe.getValue() && mc.player != null && !mc.player.isFallFlying() && mc.player.getVehicle() == null) {
                return;
            }
            event.cancel();
        }
        if (spoofJump.getValue() && (cmd.getAction() == ServerboundPlayerCommandPacket.Action.START_RIDING_JUMP
            || cmd.getAction() == ServerboundPlayerCommandPacket.Action.STOP_RIDING_JUMP)) {
            event.cancel();
        }
    }
}