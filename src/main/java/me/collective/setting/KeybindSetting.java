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
        int key = getValue();
        if (key == GLFW.GLFW_KEY_UNKNOWN || key == -1) return "None";
        if (key == -100) return "LMB";
        if (key == -101) return "RMB";
        if (key == -102) return "MMB";
        if (key < -100) return "Mouse " + (-key - 100);
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) return name.toUpperCase();
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) return "RShift";
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT) return "LShift";
        if (key == GLFW.GLFW_KEY_RIGHT_CONTROL) return "RCtrl";
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL) return "LCtrl";
        if (key == GLFW.GLFW_KEY_RIGHT_ALT) return "RAlt";
        if (key == GLFW.GLFW_KEY_LEFT_ALT) return "LAlt";
        return "Key " + key;
    }

    public boolean isPressed() {
        int key = getValue();
        if (key == GLFW.GLFW_KEY_UNKNOWN || key == -1) return false;
        long window = net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle();
        if (key <= -100) {
            int button = -key - 100;
            return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    public boolean isNone() {
        return getValue() == null || getValue() <= 0 || getValue() == GLFW.GLFW_KEY_UNKNOWN;
    }

    public int compute() {
        return getValue() != null ? getValue() : -1;
    }

    public KeybindSetting handle(java.util.function.Supplier<Boolean> hiddenSupplier) {
        setVisibilityCondition(hiddenSupplier);
        return this;
    }
}
