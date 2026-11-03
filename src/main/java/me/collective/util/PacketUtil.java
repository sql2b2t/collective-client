package me.collective.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class PacketUtil {
    /**
     * Weak-семантика обязательна: пакет, для которого не вызвали
     * checkAndRemoveSilent, не должен копиться вечно. Доступ идёт из разных
     * потоков (клиентский тик и netty), поэтому обычный WeakHashMap обёрнут
     * в synchronized-обёртку: цена копеечная, целостность множества гарантирована.
     */
    private static final Set<Packet<?>> SILENT_PACKETS =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public static void sendSilently(Packet<?> packet) {
        SILENT_PACKETS.add(packet);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            mc.getNetworkHandler().getConnection().send(packet);
        }
    }

    public static boolean checkAndRemoveSilent(Packet<?> packet) {
        return SILENT_PACKETS.remove(packet);
    }
}
