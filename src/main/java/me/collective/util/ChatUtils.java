package me.collective.util;

import net.minecraft.client.Minecraft;

public class ChatUtils {
    public static void sendPlayerMsg(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        if (msg.startsWith("/")) {
            mc.getConnection().sendCommand(msg.substring(1));
        } else {
            mc.getConnection().sendChat(msg);
        }
    }
}