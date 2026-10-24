package me.collective.setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class DoubleSetting {
    public static class Builder {
        private String name = "";
        private String description = "";
        private double defaultValue = 0.0;
        private double min = 0.0;
        private double max = 100.0;
        private Supplier<Boolean> visible = null;
        private Consumer<Double> onChanged = null;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder defaultValue(double defaultValue) { this.defaultValue = defaultValue; return this; }
        public Builder min(double min) { this.min = min; return this; }
        public Builder max(double max) { this.max = max; return this; }
        public Builder sliderMin(double min) { this.min = min; return this; }
        public Builder sliderMax(double max) { this.max = max; return this; }
        public Builder sliderRange(double min, double max) { this.min = min; this.max = max; return this; }
        public Builder visible(Supplier<Boolean> visible) { this.visible = visible; return this; }
        public Builder onChanged(Consumer<Double> onChanged) { this.onChanged = onChanged; return this; }

        public NumberSetting build() {
            NumberSetting s = new NumberSetting(name, defaultValue, min, max, 0.01, visible != null ? (ignored) -> visible.get() : null, false);
            if (onChanged != null) s.setChangeListener(onChanged);
            return s;
        }
    }
}