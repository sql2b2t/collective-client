package me.collective.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;

public class PacketUtil {

    private PacketUtil() {
    }

    /**
     * Отправляет пакет напрямую в соединение, минуя обёртки уровня
     * ClientPlayNetworkHandler. Исторически здесь был реестр «тихих» пакетов,
     * который никто не читал — механизм удалён как мёртвый: пакет всё равно
     * проходит через PacketEvent.Send (миксин висит на ClientConnection.send).
     */
    public static void sendSilently(Packet<?> packet) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            mc.getNetworkHandler().getConnection().send(packet);
        }
    }
}
