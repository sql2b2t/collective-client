package me.collective.setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class IntSetting {
    public static class Builder {
        private String name = "";
        private String description = "";
        private int defaultValue = 0;
        private int min = 0;
        private int max = 100;
        private Supplier<Boolean> visible = null;
        private Consumer<Integer> onChanged = null;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder defaultValue(int defaultValue) { this.defaultValue = defaultValue; return this; }
        public Builder min(int min) { this.min = min; return this; }
        public Builder max(int max) { this.max = max; return this; }
        public Builder sliderMin(int min) { this.min = min; return this; }
        public Builder sliderMax(int max) { this.max = max; return this; }
        public Builder sliderRange(int min, int max) { this.min = min; this.max = max; return this; }
        public Builder visible(Supplier<Boolean> visible) { this.visible = visible; return this; }
        public Builder onChanged(Consumer<Integer> onChanged) { this.onChanged = onChanged; return this; }

        public Setting<Integer> build() {
            IntegerSetting s = new IntegerSetting(name, defaultValue, min, max, visible != null ? (ignored) -> visible.get() : null);
            if (onChanged != null) s.setChangeListener(onChanged);
            return s;
        }
    }
}