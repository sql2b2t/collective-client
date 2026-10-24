package me.collective.addon;

import me.collective.category.Category;
import me.collective.command.Command;
import me.collective.command.CommandManager;
import me.collective.event.EventBus;
import me.collective.font.FontManager;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.module.hud.HudModule;
import me.collective.render.BlurEngine;
import me.collective.util.BaritoneBridge;
import me.collective.util.InventoryUtil;
import me.collective.util.RotationUtils;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public abstract class CollectiveAddon {
    protected final Minecraft mc = Minecraft.getInstance();
    private String name;
    private String version = "1.0.0";
    private String author = "Unknown";
    private String description = "";
    private String website;

    private final List<Module> registeredModules = new ArrayList<>();
    private final List<Command> registeredCommands = new ArrayList<>();

    public void initMetadata(String name, String version, String author, String description, String website) {
        this.name = name;
        if (version != null) this.version = version;
        if (author != null) this.author = author;
        if (description != null) this.description = description;
        this.website = website;
    }

    /**
     * Вызывается при загрузке аддона.
     */
    public abstract void onInitialize();

    /**
     * Вызывается при выгрузке/перезагрузке аддона.
     */
    public void onShutdown() {
        // Автоматически очищаем зарегистрированные модули и команды при выгрузке аддона
        for (Module m : registeredModules) {
            if (m.isToggled()) m.setToggled(false);
            ModuleManager.INSTANCE.getModules().remove(m);
        }
        registeredModules.clear();

        for (Command c : registeredCommands) {
            CommandManager.INSTANCE.getCommands().remove(c);
        }
        registeredCommands.clear();

        // Отписываем аддон от EventBus
        EventBus.INSTANCE.unsubscribe(this);
    }

    /**
     * Регистрация обычного или HUD модуля.
     */
    public void registerModule(Module module) {
        registeredModules.add(module);
        ModuleManager.INSTANCE.register(module);
    }

    /**
     * Регистрация HUD элемента.
     */
    public void registerHud(HudModule hudModule) {
        registerModule(hudModule);
    }

    /**
     * Регистрация команды чата.
     */
    public void registerCommand(Command command) {
        registeredCommands.add(command);
        CommandManager.INSTANCE.register(command);
    }

    /**
     * Подписка объекта на событийную шину.
     */
    public void subscribeEvents(Object listener) {
        EventBus.INSTANCE.subscribe(listener);
    }

    /**
     * Отписка от событийной шины.
     */
    public void unsubscribeEvents(Object listener) {
        EventBus.INSTANCE.unsubscribe(listener);
    }

    // --- Удобный доступ к внутренним системам клиента ---

    public ModuleManager getModuleManager() {
        return ModuleManager.INSTANCE;
    }

    public CommandManager getCommandManager() {
        return CommandManager.INSTANCE;
    }

    public EventBus getEventBus() {
        return EventBus.INSTANCE;
    }

    public RotationUtils getRotations() {
        return RotationUtils.getInstance();
    }

    public BlurEngine getBlurEngine() {
        return BlurEngine.INSTANCE;
    }

    public FontManager getFontManager() {
        return FontManager.INSTANCE;
    }

    public String getName() {
        return name != null ? name : getClass().getSimpleName();
    }

    public String getVersion() {
        return version;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public String getWebsite() {
        return website;
    }

    public List<Module> getRegisteredModules() {
        return registeredModules;
    }

    public List<Command> getRegisteredCommands() {
        return registeredCommands;
    }
}