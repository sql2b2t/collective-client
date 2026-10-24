package me.collective.module.client;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.util.BaritoneHelper;

import java.awt.Color;

public class Baritone extends Module {
    public static Baritone INSTANCE;

    public final BooleanSetting freeLook = add(new BooleanSetting("FreeLook", true));
    public final BooleanSetting disconnectOnArrival = add(new BooleanSetting("DisconnectOnArrival", false));
    public final BooleanSetting censorCoordinates = add(new BooleanSetting("CensorCoordinates", false));
    public final BooleanSetting customColors = add(new BooleanSetting("CustomColors", true));
    public final ColorSetting colorPath = add(new ColorSetting("ColorPath", new Color(189, 153, 255, 255)));
    public final ColorSetting colorGoal = add(new ColorSetting("ColorGoal", new Color(255, 85, 255, 255)));

    public Baritone() {
        super("Baritone", "Настройки интеграции с Baritone и цвета отображения путей.", Category.CLIENT);
        INSTANCE = this;
    }

    @Override
    public void onUpdate() {
        if (!BaritoneHelper.isAvailable()) return;

        try {
            Settings settings = BaritoneAPI.getSettings();
            if (settings != null) {
                settings.freeLook.value = freeLook.getValue();
                settings.disconnectOnArrival.value = disconnectOnArrival.getValue();
                settings.censorCoordinates.value = censorCoordinates.getValue();
                if (customColors.getValue()) {
                    settings.colorCurrentPath.value = colorPath.getValue();
                    settings.colorGoalBox.value = colorGoal.getValue();
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
