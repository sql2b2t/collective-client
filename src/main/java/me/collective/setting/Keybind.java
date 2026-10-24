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
        if (key <= 0) return false;
        if (isButton) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.mouseHandler != null) {
                if (key == 0) return mc.mouseHandler.isLeftPressed();
                if (key == 1) return mc.mouseHandler.isRightPressed();
                if (key == 2) return mc.mouseHandler.isMiddlePressed();
            }
            return false;
        }
        return me.collective.util.KeyHelper.isKeyDown(key);
    }

    public boolean isNone() {
        return key <= 0;
    }

    public static Keybind none() {
        return new Keybind(-1, false);
    }

    public static Keybind fromKey(int key) {
        return new Keybind(key, false);
    }

    public static Keybind fromButton(int button) {
        return new Keybind(button, true);
    }
}