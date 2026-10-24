package me.collective.module;

import me.collective.category.Category;
import me.collective.setting.KeybindSetting;
import me.collective.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    protected static final Minecraft mc = Minecraft.getInstance();
    public static final Minecraft client = mc;

    private final String name;
    private final String description;
    private final Category category;
    private boolean toggled;
    private final KeybindSetting keybind;
    private final List<Setting<?>> settingsList = new ArrayList<>();
    protected final me.collective.setting.SettingsManager settings = new me.collective.setting.SettingsManager(this);

    public Module(String name, String description, Category category) {
        this(name, description, category, GLFW.GLFW_KEY_UNKNOWN);
    }

    public Module(String name, String description, Category category, int defaultKey) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keybind = new KeybindSetting("Keybind", defaultKey);
        this.settingsList.add(this.keybind);
    }

    public <T extends Setting<?>> T add(T setting) {
        this.settingsList.add(setting);
        return setting;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isToggled() {
        return toggled;
    }

    public boolean isEnabled() {
        return toggled;
    }

    public boolean isActive() {
        return toggled;
    }

    public void setToggled(boolean toggled) {
        if (this.toggled != toggled) {
            this.toggled = toggled;
            if (toggled) {
                me.collective.event.EventBus.INSTANCE.subscribe(this);
                onEnable();
                onActivate();
            } else {
                me.collective.event.EventBus.INSTANCE.unsubscribe(this);
                onDisable();
                onDeactivate();
            }
            if (mc.player != null && me.collective.module.client.Notifications.INSTANCE != null) {
                me.collective.module.client.Notifications.INSTANCE.notifyModule(this, toggled);
            }
            try {
                me.collective.config.ConfigManager.getInstance().markDirty();
            } catch (Throwable ignored) {
            }
        }
    }

    public void toggle() {
        setToggled(!toggled);
    }

    public KeybindSetting getKeybind() {
        return keybind;
    }

    public List<Setting<?>> getSettings() {
        return settingsList;
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onActivate() {}
    public void onDeactivate() {}
    public void onUpdate() {}
    public void onWorldChange() {}
    public void onRender2D(GuiGraphics context, float delta) {}
    public String getInfoString() { return null; }

    public void error(String msg) {
        me.collective.command.ChatHelper.sendMessage(net.minecraft.text.Component.literal("§c" + msg));
    }

    public void info(String msg) {
        me.collective.command.ChatHelper.sendMessage(net.minecraft.text.Component.literal("§7[§b" + getName() + "§7] §f" + msg));
    }

    public void info(String format, Object... args) {
        info(String.format(format, args));
    }

    public void warning(String msg) {
        me.collective.command.ChatHelper.sendMessage(net.minecraft.text.Component.literal("§e" + msg));
    }

    public void warning(String format, Object... args) {
        warning(String.format(format, args));
    }
}