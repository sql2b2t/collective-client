package me.collective.font;

import me.collective.module.ModuleManager;
import me.collective.module.client.Fonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.Optional;

/**
 * Общий хелпер кастомного шрифта для обоих бэкендов
 * ({@code ChatHud$Hud} и {@code ChatHud$Interactable}).
 */
public final class ChatFontUtil {
    private ChatFontUtil() {
    }

    /** Возвращает true если отрисовали сами и ваниллу надо пропустить. */
    public static boolean drawCustomChat(DrawContext context, int y, float opacity, OrderedText text) {
        Fonts fonts = ModuleManager.INSTANCE.getModule(Fonts.class);
        if (fonts == null || !fonts.isToggled() || !fonts.chat.getValue()) return false;
        if (text == null) return false;

        // X=4 уже заложен в матрицах (translate), Y строки приходит параметром
        int color = (Math.max(0, Math.min(255, (int) (opacity * 255))) << 24) | 0xFFFFFF;
        FontManager.INSTANCE.drawStringWithShadow(context, toSectionString(text), 0, y, color);
        return true;
    }

    /** OrderedText со стилями → строка с §-кодами. */
    public static String toSectionString(OrderedText text) {
        StringBuilder sb = new StringBuilder("§r§f");
        final int[] last = {-1, 0, 0, 0, 0, 0}; // colorRgb, bold, italic, underline, strike, obfuscated
        final boolean[] first = {true};
        text.accept((index, style, codePoint) -> {
            emitStyle(sb, last, first, style);
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    /** Text со стилями → строка с §-кодами (сегменты сохраняют свои стили). */
    public static String toSectionString(Text text) {
        StringBuilder sb = new StringBuilder("§r§f");
        final int[] last = {-1, 0, 0, 0, 0, 0};
        final boolean[] first = {true};
        text.visit((style, str) -> {
            emitStyle(sb, last, first, style);
            sb.append(str);
            return Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }

    private static void emitStyle(StringBuilder sb, int[] last, boolean[] first, Style style) {
        int rgb = -1;
        TextColor color = style.getColor();
        if (color != null) rgb = color.getRgb();
        int bold = style.isBold() ? 1 : 0;
        int italic = style.isItalic() ? 1 : 0;
        int underline = style.isUnderlined() ? 1 : 0;
        int strike = style.isStrikethrough() ? 1 : 0;
        int obf = style.isObfuscated() ? 1 : 0;
        if (first[0] || rgb != last[0] || bold != last[1] || italic != last[2]
            || underline != last[3] || strike != last[4] || obf != last[5]) {
            first[0] = false;
            last[0] = rgb;
            last[1] = bold;
            last[2] = italic;
            last[3] = underline;
            last[4] = strike;
            last[5] = obf;
            sb.append('§').append(nearestCode(rgb));
            if (bold == 1) sb.append("§l");
            if (italic == 1) sb.append("§o");
            if (underline == 1) sb.append("§n");
            if (strike == 1) sb.append("§m");
            if (obf == 1) sb.append("§k");
        }
    }

    /** Ближайший §-цвет к RGB. */
    private static char nearestCode(int rgb) {
        if (rgb < 0) return 'f';
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int[] table = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
            0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
            0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
        };
        char[] codes = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        int best = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < table.length; i++) {
            int c = table[i];
            long dr = r - ((c >> 16) & 0xFF);
            long dg = g - ((c >> 8) & 0xFF);
            long db = b - (c & 0xFF);
            long dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return codes[best];
    }
}
