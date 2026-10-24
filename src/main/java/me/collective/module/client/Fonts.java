package me.collective.module.client;

import me.collective.category.Category;
import me.collective.font.FontManager;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;

import java.awt.Font;

public class Fonts extends Module {
    public static Fonts INSTANCE;
    private long fontChangeAt;
    private boolean fontPending;

    public enum FontType {
        Arial,
        Verdana,
        Tahoma,
        Segoe_UI,
        Roboto,
        SansSerif
    }

    public final EnumSetting<FontType> fontType = add(new EnumSetting<>("Font", FontType.Arial));
    public final NumberSetting size = add(new NumberSetting("Size", 8.5, 6.0, 24.0, 0.5));
    public final BooleanSetting bold = add(new BooleanSetting("Bold", false));
    public final BooleanSetting italic = add(new BooleanSetting("Italic", false));
    public final BooleanSetting antiAlias = add(new BooleanSetting("AntiAlias", true));
    public final BooleanSetting shadow = add(new BooleanSetting("Shadow", true));
    public final BooleanSetting chat = add(new BooleanSetting("Чат", true));
    public final BooleanSetting bossBar = add(new BooleanSetting("Боссбар", true));
    public final BooleanSetting tab = add(new BooleanSetting("Таб", true));
    public final BooleanSetting inventory = add(new BooleanSetting("Инвентарь", true));

    public Fonts() {
        super("Fonts", "Управление кастомными шрифтами и сглаживанием текста.", Category.CLIENT);
        INSTANCE = this;
        setToggled(true);

        fontType.setChangeListener(s -> requestFontUpdate());
        size.setChangeListener(s -> requestFontUpdate());
        bold.setChangeListener(s -> requestFontUpdate());
        italic.setChangeListener(s -> requestFontUpdate());
        antiAlias.setChangeListener(s -> requestFontUpdate());
    }

    private void requestFontUpdate() {
        fontPending = true;
        fontChangeAt = System.nanoTime();
    }

    @Override
    public void onUpdate() {
        if (fontPending && System.nanoTime() - fontChangeAt >= 150_000_000L) updateFont();
    }

    public void updateFont() {
        fontPending = false;
        int style = Font.PLAIN;
        if (bold.getValue() && italic.getValue()) {
            style = Font.BOLD | Font.ITALIC;
        } else if (bold.getValue()) {
            style = Font.BOLD;
        } else if (italic.getValue()) {
            style = Font.ITALIC;
        }

        String name = fontType.getValue().name().replace('_', ' ');
        FontManager.INSTANCE.setFont(name, size.getFloatValue(), style, antiAlias.getValue());
    }

    @Override
    public void onEnable() {
        updateFont();
    }

    @Override
    public void onDisable() {
        // Will seamlessly fallback to Minecraft's TextRenderer
    }
}