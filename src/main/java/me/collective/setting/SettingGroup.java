package me.collective.setting;

import me.collective.module.Module;

public class SettingGroup {
    private final String name;
    private final Module module;

    public SettingGroup(String name, Module module) {
        this.name = name;
        this.module = module;
    }

    public <T extends Setting<?>> T add(T setting) {
        return module.add(setting);
    }
}