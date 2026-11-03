package me.collective.setting;

import java.util.function.Predicate;

public class EnumSetting<T extends Enum<T>> extends Setting<T> {
    private final T[] values;

    public EnumSetting(String name, T defaultValue) {
        this(name, defaultValue, null);
    }

    public EnumSetting(String name, T defaultValue, Predicate<Boolean> visible) {
        super(name, defaultValue, visible);
        this.values = (T[]) defaultValue.getDeclaringClass().getEnumConstants();
    }

    public T[] getValues() {
        return values;
    }

    public void cycle() {
        int index = getValue().ordinal();
        int next = (index + 1) % values.length;
        setValue(values[next]);
    }

    public void cycleReverse() {
        int index = getValue().ordinal();
        int prev = (index - 1 + values.length) % values.length;
        setValue(values[prev]);
    }


    public boolean setByName(String name) {
        for (T val : values) {
            if (val.name().equalsIgnoreCase(name)) {
                setValue(val);
                return true;
            }
        }
        // Older profiles stored the displayed (possibly localized) label.
        for (T val : values) {
            if (val.toString().equalsIgnoreCase(name)) {
                setValue(val);
                return true;
            }
        }
        return false;
    }

    public static class Builder<T extends Enum<T>> {
        private String name = "";
        private String description = "";
        private T defaultValue = null;
        private java.util.function.Supplier<Boolean> visible = null;
        private java.util.function.Consumer<T> onChanged = null;

        public Builder<T> name(String name) { this.name = name; return this; }
        public Builder<T> description(String description) { this.description = description; return this; }
        public Builder<T> defaultValue(T defaultValue) { this.defaultValue = defaultValue; return this; }
        public Builder<T> visible(java.util.function.Supplier<Boolean> visible) { this.visible = visible; return this; }
        public Builder<T> onChanged(java.util.function.Consumer<T> onChanged) { this.onChanged = onChanged; return this; }

        public EnumSetting<T> build() {
            EnumSetting<T> s = new EnumSetting<>(name, defaultValue, visible != null ? (ignored) -> visible.get() : null);
            if (onChanged != null) s.setChangeListener(onChanged);
            return s;
        }
    }
}
