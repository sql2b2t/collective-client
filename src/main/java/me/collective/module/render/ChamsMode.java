package me.collective.module.render;

public enum ChamsMode {
    BOTH("Both"),
    FILL("Fill"),
    LINE("Line"),
    OFF("Off");

    private final String name;

    ChamsMode(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return this != OFF;
    }

    @Override
    public String toString() {
        return name;
    }
}