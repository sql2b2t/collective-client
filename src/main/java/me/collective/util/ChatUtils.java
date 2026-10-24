package me.collective.util;

import net.minecraft.client.Minecraft;

public class ChatUtils {
    public static void sendPlayerMsg(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getNetworkHandler() == null) return;
        if (msg.startsWith("/")) {
            mc.getNetworkHandler().sendChatCommand(msg.substring(1));
        } else {
            mc.getNetworkHandler().sendChatMessage(msg);
        }
    }
}