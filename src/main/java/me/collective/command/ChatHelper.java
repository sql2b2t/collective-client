package me.collective.command;

import me.collective.module.client.Colors;
import me.collective.module.client.UI;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;

import java.awt.Color;

public class ChatHelper {
    private static final Minecraft mc = Minecraft.getInstance();

    public static Component getPrefix() {
        Color primary = getPrimaryColor();
        TextColor primaryTc = TextColor.fromRgb(primary.getRGB() & 0xFFFFFF);
        TextColor bracketTc = TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY);

        MutableComponent prefix = Component.empty();
        prefix.append(Component.literal("[").withStyle(s -> s.withColor(bracketTc)));
        prefix.append(Component.literal("Collective").withStyle(s -> s.withColor(primaryTc).withBold(false)));
        prefix.append(Component.literal("]").withStyle(s -> s.withColor(bracketTc)));
        return prefix;
    }

    public static Color getPrimaryColor() {
        if (Colors.INSTANCE != null && Colors.INSTANCE.themeColor != null) {
            return Colors.INSTANCE.themeColor.getValue();
        }
        if (UI.INSTANCE != null && UI.INSTANCE.color != null) {
            return UI.INSTANCE.color.getValue();
        }
        return new Color(189, 153, 255);
    }

    public static TextColor getPrimaryTextColor() {
        return TextColor.fromRgb(getPrimaryColor().getRGB() & 0xFFFFFF);
    }

    public static void sendMessage(Component text) {
        MutableComponent full = Component.empty().append(getPrefix()).append(" ").append(text);
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(full);
        } else if (mc.player != null) {
            mc.player.sendSystemMessage(full);
        }
    }

    public static void sendMessage(String text) {
        sendMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }
}