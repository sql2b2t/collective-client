package me.collective.setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class BoolSetting extends BooleanSetting {
    public BoolSetting(String name, boolean defaultValue) {
        super(name, defaultValue);
    }

    public static class Builder {
        private String name = "";
        private String description = "";
        private boolean defaultValue = false;
        private Supplier<Boolean> visible = null;
        private Consumer<Boolean> onChanged = null;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder defaultValue(boolean defaultValue) { this.defaultValue = defaultValue; return this; }
        public Builder visible(Supplier<Boolean> visible) { this.visible = visible; return this; }
        public Builder onChanged(Consumer<Boolean> onChanged) { this.onChanged = onChanged; return this; }

        public BooleanSetting build() {
            BooleanSetting s = new BooleanSetting(name, defaultValue, visible != null ? (ignored) -> visible.get() : null);
            if (onChanged != null) s.setChangeListener(onChanged);
            return s;
        }
    }
}