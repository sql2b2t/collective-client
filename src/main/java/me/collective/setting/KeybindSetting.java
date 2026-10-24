package me.collective.setting;

import org.lwjgl.glfw.GLFW;

public class KeybindSetting extends Setting<Integer> {
    public KeybindSetting(String name, int defaultKey) {
        super(name, defaultKey);
    }

    public static class Builder {
        private String name = "";
        private String description = "";
        private Keybind defaultValue = Keybind.none();
        private java.util.function.Supplier<Boolean> visible = null;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder defaultValue(Keybind key) { this.defaultValue = key; return this; }
        public Builder defaultValue(int key) { this.defaultValue = new Keybind(key); return this; }
        public Builder visible(java.util.function.Supplier<Boolean> visible) { this.visible = visible; return this; }

        public Setting<Keybind> build() {
            return new Setting<>(name, defaultValue, visible != null ? (ignored) -> visible.get() : null);
        }
    }

    public String getKeyName() {
        Integer v = getValue();
        return v != null ? getKeyName(v) : "None";
    }

    public static String formatKey(int key, boolean isButton) {
        if (key == GLFW.GLFW_KEY_UNKNOWN || key == -1) return "None";
        if (isButton || key <= -100) {
            int button = isButton ? key : -key - 100;
            if (button == 0) return "LMB";
            if (button == 1) return "RMB";
            if (button == 2) return "MMB";
            return "Mouse " + button;
        }
        return getKeyName(key);
    }

    public static String getKeyName(int key) {
        if (key <= 0 || key == -1) return "None";
        if (key == -100) return "LMB";
        if (key == -101) return "RMB";
        if (key == -102) return "MMB";
        if (key < -100) return "Mouse " + (-key - 100);

        int k263 = me.collective.util.KeyHelper.to263Key(key);
        if (me.collective.util.KeyHelper.isRightShift(key)) return "RShift";
        if (k263 == com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT) return "LShift";
        if (k263 == com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL) return "RCtrl";
        if (k263 == com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL) return "LCtrl";
        if (k263 == com.mojang.blaze3d.platform.InputConstants.KEY_RALT) return "RAlt";
        if (k263 == com.mojang.blaze3d.platform.InputConstants.KEY_LALT) return "LAlt";
        if (k263 == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) return "Esc";
        if (k263 == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN) return "Enter";
        if (k263 == com.mojang.blaze3d.platform.InputConstants.KEY_TAB) return "Tab";
        if (k263 == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE) return "Backspace";
        if (k263 == com.mojang.blaze3d.platform.InputConstants.KEY_DELETE) return "Delete";
        if (k263 == com.mojang.blaze3d.platform.InputConstants.KEY_SPACE) return "Space";

        try {
            com.mojang.blaze3d.platform.InputConstants.Key k = com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD.getOrCreate(k263);
            if (k != null && k.getDisplayName() != null) {
                String str = k.getDisplayName().getString();
                if (str != null && !str.trim().isEmpty()) {
                    return str;
                }
            }
        } catch (Throwable ignored) {}

        return "Key " + key;
    }

    public boolean isPressed() {
        int key = getValue() != null ? getValue() : -1;
        if (key <= 0 || key == -1) return false;
        if (key <= -100) {
            int button = -key - 100;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.mouseHandler != null) {
                if (button == 0) return mc.mouseHandler.isLeftPressed();
                if (button == 1) return mc.mouseHandler.isRightPressed();
                if (button == 2) return mc.mouseHandler.isMiddlePressed();
            }
            return false;
        }
        return me.collective.util.KeyHelper.isKeyDown(key);
    }

    public boolean isDown() {
        return isPressed();
    }

    public boolean isNone() {
        return getValue() == null || getValue() <= 0 || getValue() == -1;
    }

    public int compute() {
        return getValue() != null ? getValue() : -1;
    }

    public KeybindSetting handle(java.util.function.Supplier<Boolean> hiddenSupplier) {
        setVisibilityCondition(hiddenSupplier);
        return this;
    }
}