package me.collective.module.client;

import me.collective.category.Category;
import me.collective.gui.hud.HudEditorScreen;
import me.collective.module.Module;
import me.collective.render.ColorUtil;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.NumberSetting;

import java.awt.Color;

public class HUD extends Module {
    public static HUD INSTANCE;

    public final BooleanSetting hudEditor = add(new BooleanSetting("HudEditor", false));
    public final ColorSetting color = add(new ColorSetting("Color", new Color(189, 153, 255, 255)));
    public final ColorSetting secondColor = add(new ColorSetting("SecondColor", new Color(105, 86, 143, 255)));
    public final BooleanSetting rainbow = add(new BooleanSetting("Rainbow", false));
    public final NumberSetting rainbowSpeed = add(new NumberSetting("RainbowSpeed", 1.0, 0.1, 5.0, 0.1));
    public final NumberSetting padding = add(new NumberSetting("Padding", 2, 0, 10, 1));

    public HUD() {
        super("HUD", "Отображение наэкранных информационных элементов и виджетов.", Category.CLIENT);
        INSTANCE = this;
        setToggled(true);

        hudEditor.setChangeListener(val -> {
            if (val) {
                hudEditor.setValue(false);
                mc.setScreen(HudEditorScreen.getInstance());
            }
        });
    }

    public Color getHudColor(int index) {
        if (rainbow.getValue()) {
            return ColorUtil.rainbow((int) (index * 100 * rainbowSpeed.getFloatValue()), 0.8f, 1.0f);
        }
        if (secondColor != null) {
            float factor = (float) (Math.sin(System.currentTimeMillis() / 450.0 + index * 0.25) + 1.0) / 2.0f;
            return ColorUtil.gradient(color.getColor(), secondColor.getColor(), factor);
        }
        return color.getColor();
    }
}