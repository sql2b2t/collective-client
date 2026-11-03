package me.collective.font;

import java.awt.Font;
import java.io.InputStream;

/**
 * Shared high-DPI TrueType font set for modern Glassmorphism GUIs
 * (AltManagerScreen). Prevents duplicate atlas rasterization
 * and texture allocation.
 */
public final class ModernFonts {

    public static CFont fontTitle;
    public static CFont fontTabs;
    public static CFont fontName;
    public static CFont fontBadge;
    public static CFont fontStatus;
    public static CFont fontBtn;
    public static CFont fontSearch;

    private static boolean initialized = false;

    private ModernFonts() {}

    public static synchronized void ensureInitialized() {
        if (initialized) return;
        long start = System.nanoTime();
        try {
            Font baseBold = loadRawFont("inter-medium.ttf", Font.BOLD);
            Font basePlain = loadRawFont("inter-medium.ttf", Font.PLAIN);

            fontTitle = new CFont(baseBold.deriveFont(Font.BOLD, 18.0f), true, true);
            fontTabs = new CFont(basePlain.deriveFont(Font.PLAIN, 13.0f), true, true);
            fontName = new CFont(baseBold.deriveFont(Font.BOLD, 14.5f), true, true);
            fontBadge = new CFont(basePlain.deriveFont(Font.PLAIN, 11.0f), true, true);
            fontStatus = new CFont(basePlain.deriveFont(Font.PLAIN, 11.5f), true, true);
            fontBtn = new CFont(baseBold.deriveFont(Font.BOLD, 12.0f), true, true);
            fontSearch = new CFont(basePlain.deriveFont(Font.PLAIN, 12.0f), true, true);
            initialized = true;
        } catch (Exception e) {
            Font fb = new Font("SansSerif", Font.PLAIN, 13);
            fontTitle = fontTabs = fontName = fontBadge = fontStatus = fontBtn = fontSearch = new CFont(fb, true, true);
            initialized = true;
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        System.out.println("[Collective] ModernFonts initialized in " + elapsedMs + " ms");
    }

    private static Font loadRawFont(String filename, int style) {
        try (InputStream is = ModernFonts.class.getResourceAsStream("/assets/collective/font/" + filename)) {
            if (is != null) return Font.createFont(Font.TRUETYPE_FONT, is);
        } catch (Exception ignored) {}
        try (InputStream is = ModernFonts.class.getResourceAsStream("/assets/collective/fonts/" + filename)) {
            if (is != null) return Font.createFont(Font.TRUETYPE_FONT, is);
        } catch (Exception ignored) {}
        return new Font("SansSerif", style, 12);
    }
}
