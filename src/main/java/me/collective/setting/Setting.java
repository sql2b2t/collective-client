package me.collective.setting;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class Setting<T> {
    private final String name;
    private T value;
    private final Predicate<Boolean> visible;
    private java.util.function.Supplier<Boolean> visibilityCondition;
    private Consumer<T> changeListener;

    public Setting(String name, T defaultValue) {
        this(name, defaultValue, null);
    }

    public Setting(String name, T defaultValue, Predicate<Boolean> visible) {
        this.name = name;
        this.value = defaultValue;
        this.visible = visible;
    }

    public String getName() {
        return name;
    }

    public T getValue() {
        return value;
    }

    public T get() {
        return getValue();
    }

    public void set(T val) {
        setValue(val);
    }

    public void setValue(T value) {
        if (java.util.Objects.equals(this.value, value)) {
            return;
        }
        this.value = value;
        if (changeListener != null) {
            changeListener.accept(value);
        }
        // Любое изменение настройки помечает конфиг для автосейва
        try {
            me.collective.config.ConfigManager.getInstance().markDirty();
        } catch (Throwable ignored) {
        }
    }

    public void setValueQuietly(T value) {
        this.value = value;
    }

    public boolean isVisible() {
        return (visible == null || visible.test(true))
            && (visibilityCondition == null || Boolean.TRUE.equals(visibilityCondition.get()));
    }

    protected void setVisibilityCondition(java.util.function.Supplier<Boolean> condition) {
        this.visibilityCondition = condition;
    }

    public Setting<T> setChangeListener(Consumer<T> listener) {
        this.changeListener = listener;
        return this;
    }
}
