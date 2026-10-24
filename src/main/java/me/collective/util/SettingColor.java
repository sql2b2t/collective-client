package me.collective.util;

import java.awt.Color;

public class SettingColor {
    public int r, g, b, a;

    public SettingColor() {
        this(255, 255, 255, 255);
    }

    public SettingColor(Color awt) {
        this(awt.getRed(), awt.getGreen(), awt.getBlue(), awt.getAlpha());
    }

    public SettingColor(int r, int g, int b) {
        this(r, g, b, 255);
    }

    public SettingColor(int r, int g, int b, int a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public void set(int r, int g, int b, int a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public Color toAwt() {
        return new Color(r, g, b, a);
    }
}