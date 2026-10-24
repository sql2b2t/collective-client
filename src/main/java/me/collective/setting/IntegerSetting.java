package me.collective.setting;

import java.util.function.Predicate;

public class IntegerSetting extends Setting<Integer> {
    private final int min;
    private final int max;

    public IntegerSetting(String name, int defaultValue, int min, int max) {
        this(name, defaultValue, min, max, null);
    }

    public IntegerSetting(String name, int defaultValue, int min, int max, Predicate<Boolean> visible) {
        super(name, defaultValue, visible);
        this.min = min;
        this.max = max;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public int getIntValue() {
        return getValue() != null ? getValue() : 0;
    }

    @Override
    public void setValue(Integer value) {
        if (value == null) return;
        int clamped = Math.max(min, Math.min(max, value));
        super.setValue(clamped);
    }
}