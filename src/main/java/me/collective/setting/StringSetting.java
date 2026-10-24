package me.collective.setting;

import java.util.function.Predicate;

public class StringSetting extends Setting<String> {
    public StringSetting(String name, String defaultValue) {
        super(name, defaultValue);
    }

    public StringSetting(String name, String defaultValue, Predicate<Boolean> visible) {
        super(name, defaultValue, visible);
    }
}