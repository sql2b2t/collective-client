package me.collective.setting;

import java.util.function.Predicate;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, boolean defaultValue) {
        super(name, defaultValue);
    }

    public BooleanSetting(String name, boolean defaultValue, Predicate<Boolean> visible) {
        super(name, defaultValue, visible);
    }

    public void toggle() {
        setValue(!getValue());
    }

    public boolean compute() {
        return getValue();
    }

    public BooleanSetting handle(java.util.function.Supplier<Boolean> hiddenSupplier) {
        setVisibilityCondition(hiddenSupplier);
        return this;
    }
}