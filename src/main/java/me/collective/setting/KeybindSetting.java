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
        if (key == GLFW.GLFW_KEY_UNKNOWN || key <= 0) return "None";
        if (key == -100) return "LMB";
        if (key == -101) return "RMB";
        if (key == -102) return "MMB";
        if (key < -100) return "Mouse " + (-key - 100);

        switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> { return "RShift"; }
            case GLFW.GLFW_KEY_LEFT_SHIFT -> { return "LShift"; }
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> { return "RCtrl"; }
            case GLFW.GLFW_KEY_LEFT_CONTROL -> { return "LCtrl"; }
            case GLFW.GLFW_KEY_RIGHT_ALT -> { return "RAlt"; }
            case GLFW.GLFW_KEY_LEFT_ALT -> { return "LAlt"; }
            case GLFW.GLFW_KEY_RIGHT_SUPER -> { return "RWin"; }
            case GLFW.GLFW_KEY_LEFT_SUPER -> { return "LWin"; }
            case GLFW.GLFW_KEY_MENU -> { return "Menu"; }
            case GLFW.GLFW_KEY_ESCAPE -> { return "Esc"; }
            case GLFW.GLFW_KEY_ENTER -> { return "Enter"; }
            case GLFW.GLFW_KEY_KP_ENTER -> { return "NumEnter"; }
            case GLFW.GLFW_KEY_TAB -> { return "Tab"; }
            case GLFW.GLFW_KEY_BACKSPACE -> { return "Backspace"; }
            case GLFW.GLFW_KEY_INSERT -> { return "Insert"; }
            case GLFW.GLFW_KEY_DELETE -> { return "Delete"; }
            case GLFW.GLFW_KEY_PAGE_UP -> { return "PageUp"; }
            case GLFW.GLFW_KEY_PAGE_DOWN -> { return "PageDown"; }
            case GLFW.GLFW_KEY_HOME -> { return "Home"; }
            case GLFW.GLFW_KEY_END -> { return "End"; }
            case GLFW.GLFW_KEY_CAPS_LOCK -> { return "Caps"; }
            case GLFW.GLFW_KEY_SCROLL_LOCK -> { return "Scroll"; }
            case GLFW.GLFW_KEY_NUM_LOCK -> { return "NumLock"; }
            case GLFW.GLFW_KEY_PRINT_SCREEN -> { return "PrtScn"; }
            case GLFW.GLFW_KEY_PAUSE -> { return "Pause"; }
            case GLFW.GLFW_KEY_UP -> { return "Up"; }
            case GLFW.GLFW_KEY_DOWN -> { return "Down"; }
            case GLFW.GLFW_KEY_LEFT -> { return "Left"; }
            case GLFW.GLFW_KEY_RIGHT -> { return "Right"; }
            case GLFW.GLFW_KEY_SPACE -> { return "Space"; }
        }

        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        }

        try {
            String name = GLFW.glfwGetKeyName(key, 0);
            if (name != null && !name.trim().isEmpty()) {
                return name.toUpperCase();
            }
        } catch (Throwable ignored) {}

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
