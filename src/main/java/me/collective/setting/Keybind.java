package me.collective.setting;

import org.lwjgl.glfw.GLFW;

public class Keybind {
    private int key;
    private boolean isButton;

    public Keybind(int key) {
        this(key, false);
    }

    public Keybind(int key, boolean isButton) {
        this.key = key;
        this.isButton = isButton;
    }

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public boolean isButton() {
        return isButton;
    }

    public boolean isPressed() {
        if (key < 0 || key == GLFW.GLFW_KEY_UNKNOWN) return false;
        long window = net.minecraft.client.Minecraft.getInstance().getWindow().getHandle();
        if (isButton) {
            return GLFW.glfwGetMouseButton(window, key) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    public boolean isNone() {
        return key <= 0 || key == GLFW.GLFW_KEY_UNKNOWN;
    }

    public static Keybind none() {
        return new Keybind(GLFW.GLFW_KEY_UNKNOWN, false);
    }

    public static Keybind fromKey(int key) {
        return new Keybind(key, false);
    }

    public static Keybind fromButton(int button) {
        return new Keybind(button, true);
    }
}