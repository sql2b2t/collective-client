package me.collective.module.player;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.module.Module;
import me.collective.util.RotationUtils;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

/**
 * Порт NoRotate из ThunderHack.
 *
 * <p>Игнорирует принудительные развороты от сервера, возвращает свой угол.
 */
public class NoRotate extends Module {
    public NoRotate() {
        super("NoRotate", "Блокирует развороты от сервера.", Category.PLAYER);
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundPlayerPositionPacket)) return;
        // Receive стреляет на нетти-треде: мутить состояние ротаций можно
        // только на игровом, снимаем значения и планируем.
        RotationUtils rot = RotationUtils.getInstance();
        float yaw = rot.getSentYaw();
        float pitch = rot.getSentPitch();
        mc.execute(() -> RotationUtils.setServerRotation(yaw, pitch));
    }
}