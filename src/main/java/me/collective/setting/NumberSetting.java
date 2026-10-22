package me.collective.setting;

import java.util.function.Predicate;

public class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double increment;
    private final boolean isInteger;

    public NumberSetting(String name, double defaultValue, double min, double max, double increment) {
        this(name, defaultValue, min, max, increment, null, false);
    }

    public NumberSetting(String name, int defaultValue, int min, int max, int increment) {
        this(name, (double) defaultValue, (double) min, (double) max, (double) increment, null, true);
    }

    public NumberSetting(String name, double defaultValue, double min, double max, double increment, Predicate<Boolean> visible) {
        this(name, defaultValue, min, max, increment, visible, false);
    }

    public NumberSetting(String name, int defaultValue, int min, int max, int increment, Predicate<Boolean> visible) {
        this(name, (double) defaultValue, (double) min, (double) max, (double) increment, visible, true);
    }

    public NumberSetting(String name, double defaultValue, double min, double max, double increment, Predicate<Boolean> visible, boolean isInteger) {
        super(name, defaultValue, visible);
        this.min = min;
        this.max = max;
        this.increment = increment;
        this.isInteger = isInteger;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getIncrement() {
        return increment;
    }

    public boolean isInteger() {
        return isInteger;
    }

    public int getIntValue() {
        return getValue().intValue();
    }

    public float getFloatValue() {
        return getValue().floatValue();
    }

    public float compute() {
        return getFloatValue();
    }

    public NumberSetting handle(java.util.function.Supplier<Boolean> hiddenSupplier) {
        setVisibilityCondition(hiddenSupplier);
        return this;
    }

    @Override
    public void setValue(Double value) {
        if (value == null || !Double.isFinite(value)) return;
        double clamped = Math.max(min, Math.min(max, value));
        if (increment > 0) {
            double precision = 1.0 / increment;
            clamped = Math.round(clamped * precision) / precision;
        }
        super.setValue(Math.max(min, Math.min(max, clamped)));
    }
}
