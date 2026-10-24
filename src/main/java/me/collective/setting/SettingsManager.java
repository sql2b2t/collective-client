package me.collective.setting;

import me.collective.module.Module;

public class SettingsManager {
    private final Module module;

    public SettingsManager(Module module) {
        this.module = module;
    }

    public SettingGroup getDefaultGroup() {
        return new SettingGroup("General", module);
    }

    public SettingGroup createGroup(String name) {
        return new SettingGroup(name, module);
    }
}