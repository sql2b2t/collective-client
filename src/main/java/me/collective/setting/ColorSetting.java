package me.collective.setting;

import java.awt.Color;
import java.util.function.Predicate;

public class ColorSetting extends Setting<Color> {
    private boolean rainbow = false;
    private boolean sync = false;
    private float rainbowSpeed = 1.0f;

    public ColorSetting(String name, Color defaultValue) {
        super(name, defaultValue);
    }

    public ColorSetting(String name, Color defaultValue, Predicate<Boolean> visible) {
        super(name, defaultValue, visible);
    }

    public boolean isRainbow() {
        return rainbow;
    }

    public void setRainbow(boolean rainbow) {
        this.rainbow = rainbow;
    }

    public boolean isSync() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public float getRainbowSpeed() {
        return rainbowSpeed;
    }

    public void setRainbowSpeed(float rainbowSpeed) {
        this.rainbowSpeed = rainbowSpeed;
    }

    public Color getColor() {
        if (sync && me.collective.module.client.Colors.INSTANCE != null && this != me.collective.module.client.Colors.INSTANCE.themeColor) {
            Color theme = me.collective.module.client.Colors.INSTANCE.themeColor.getValue();
            return new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), getValue().getAlpha());
        }
        if (rainbow) {
            float hue = ((System.currentTimeMillis() * rainbowSpeed) % (360 * 20)) / (360f * 20f);
            Color rgb = Color.getHSBColor(hue, 0.8f, 1.0f);
            return new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), getValue().getAlpha());
        }
        return getValue();
    }

    public int getRed() {
        return getValue().getRed();
    }

    public int getGreen() {
        return getValue().getGreen();
    }

    public int getBlue() {
        return getValue().getBlue();
    }

    public int getAlpha() {
        return getValue().getAlpha();
    }

    public void setRed(int r) {
        Color c = getValue();
        setValue(new Color(Math.max(0, Math.min(255, r)), c.getGreen(), c.getBlue(), c.getAlpha()));
    }

    public void setGreen(int g) {
        Color c = getValue();
        setValue(new Color(c.getRed(), Math.max(0, Math.min(255, g)), c.getBlue(), c.getAlpha()));
    }

    public void setBlue(int b) {
        Color c = getValue();
        setValue(new Color(c.getRed(), c.getGreen(), Math.max(0, Math.min(255, b)), c.getAlpha()));
    }

    public void setAlpha(int a) {
        Color c = getValue();
        setValue(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a))));
    }

    public static class Builder {
        private String name = "";
        private String description = "";
        private me.collective.util.SettingColor defaultValue = new me.collective.util.SettingColor();
        private java.util.function.Supplier<Boolean> visible = null;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder defaultValue(Color defaultValue) { this.defaultValue = new me.collective.util.SettingColor(defaultValue); return this; }
        public Builder defaultValue(me.collective.util.SettingColor defaultValue) { this.defaultValue = defaultValue; return this; }
        public Builder visible(java.util.function.Supplier<Boolean> visible) { this.visible = visible; return this; }

        public Setting<me.collective.util.SettingColor> build() {
            return new SettingColorSetting(name, defaultValue, visible != null ? (ignored) -> visible.get() : null);
        }
    }
}