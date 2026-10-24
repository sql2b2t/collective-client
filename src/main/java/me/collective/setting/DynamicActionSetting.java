package me.collective.setting;

import java.util.function.Supplier;

public class DynamicActionSetting extends Setting<String> {
    public DynamicActionSetting(String name, int dummy, Supplier<?> supplier) {
        super(name, "");
    }

    public DynamicActionSetting process(Supplier<Boolean> condition) {
        return this;
    }
}