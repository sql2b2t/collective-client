package me.collective.setting;

import me.collective.util.SettingColor;
import java.util.function.Predicate;

public class SettingColorSetting extends Setting<SettingColor> {
    public SettingColorSetting(String name, SettingColor defaultValue) {
        super(name, defaultValue);
    }

    public SettingColorSetting(String name, SettingColor defaultValue, Predicate<Boolean> visible) {
        super(name, defaultValue, visible);
    }
}