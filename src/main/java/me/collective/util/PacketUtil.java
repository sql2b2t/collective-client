package me.collective.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class PacketUtil {
    private static final Set<Packet<?>> SILENT_PACKETS = Collections.newSetFromMap(new WeakHashMap<>());

    public static void sendSilently(Packet<?> packet) {
        SILENT_PACKETS.add(packet);
        Minecraft mc = Minecraft.getInstance();
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            mc.getNetworkHandler().getConnection().send(packet);
        }
    }

    public static boolean checkAndRemoveSilent(Packet<?> packet) {
        return SILENT_PACKETS.remove(packet);
    }
}