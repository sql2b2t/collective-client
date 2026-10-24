package me.collective.scripting;

import me.collective.category.Category;
import me.collective.command.ChatHelper;
import me.collective.event.EventHandler;
import me.collective.event.events.*;
import me.collective.module.Module;
import me.collective.setting.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ScriptModule extends Module {

    private ScriptContext scriptContext;
    private int errorCount;

    private Consumer<ScriptModule> onEnableAction;
    private Consumer<ScriptModule> onDisableAction;
    private Consumer<ScriptModule> onTickAction;
    private Consumer<ScriptModule> onTickPostAction;
    private Consumer<PacketEvent.Send> onPacketSendAction;
    private Consumer<PacketEvent.Receive> onPacketReceiveAction;
    private Consumer<Render3DEvent> onRender3DAction;
    private BiConsumer<GuiGraphics, Float> onRender2DAction;
    private Consumer<PlayerMoveEvent> onPlayerMoveAction;
    private Consumer<StartBreakingBlockEvent> onBreakBlockAction;
    private Consumer<SendMessageEvent> onSendMessageAction;
    private Consumer<MouseClickEvent> onMouseClickAction;
    private Consumer<KeyInputEvent> onKeyInputAction;
    private Consumer<InteractEntityEvent> onInteractEntityAction;

    public ScriptModule(String name, String description, Category category) {
        super(name, description, category);
    }

    /** Привязка к скрипту-владельцу (для отчётов об ошибках). */
    public void setScriptContext(ScriptContext ctx) {
        this.scriptContext = ctx;
    }

    // --- Fluent Settings API ---

    public BooleanSetting boolSetting(String name, boolean defaultValue) {
        return add(new BooleanSetting(name, defaultValue));
    }

    public NumberSetting numberSetting(String name, double defaultValue, double min, double max, double step) {
        return add(new NumberSetting(name, defaultValue, min, max, step));
    }

    public ColorSetting colorSetting(String name, Color defaultValue) {
        return add(new ColorSetting(name, defaultValue));
    }

    public StringSetting stringSetting(String name, String defaultValue) {
        return add(new StringSetting(name, defaultValue));
    }

    public ModeSetting modeSetting(String name, String defaultMode, String... modes) {
        return add(new ModeSetting(name, defaultMode, modes));
    }

    public <T extends Enum<T>> EnumSetting<T> enumSetting(String name, T defaultValue) {
        return add(new EnumSetting<>(name, defaultValue));
    }

    // --- Event Subscriptions ---

    public ScriptModule onEnable(Consumer<ScriptModule> action) {
        this.onEnableAction = action;
        return this;
    }

    public ScriptModule onDisable(Consumer<ScriptModule> action) {
        this.onDisableAction = action;
        return this;
    }

    public ScriptModule onTick(Consumer<ScriptModule> action) {
        this.onTickAction = action;
        return this;
    }

    public ScriptModule onTickPost(Consumer<ScriptModule> action) {
        this.onTickPostAction = action;
        return this;
    }

    public ScriptModule onPacketSend(Consumer<PacketEvent.Send> action) {
        this.onPacketSendAction = action;
        return this;
    }

    public ScriptModule onPacketReceive(Consumer<PacketEvent.Receive> action) {
        this.onPacketReceiveAction = action;
        return this;
    }

    public ScriptModule onRender3D(Consumer<Render3DEvent> action) {
        this.onRender3DAction = action;
        return this;
    }

    public ScriptModule onRender2D(BiConsumer<GuiGraphics, Float> action) {
        this.onRender2DAction = action;
        return this;
    }

    public ScriptModule onPlayerMove(Consumer<PlayerMoveEvent> action) {
        this.onPlayerMoveAction = action;
        return this;
    }

    public ScriptModule onBreakBlock(Consumer<StartBreakingBlockEvent> action) {
        this.onBreakBlockAction = action;
        return this;
    }

    public ScriptModule onSendMessage(Consumer<SendMessageEvent> action) {
        this.onSendMessageAction = action;
        return this;
    }

    public ScriptModule onMouseClick(Consumer<MouseClickEvent> action) {
        this.onMouseClickAction = action;
        return this;
    }

    public ScriptModule onKeyInput(Consumer<KeyInputEvent> action) {
        this.onKeyInputAction = action;
        return this;
    }

    public ScriptModule onInteractEntity(Consumer<InteractEntityEvent> action) {
        this.onInteractEntityAction = action;
        return this;
    }

    // --- Module Lifecycle ---

    @Override
    public void onEnable() {
        dispatch(onEnableAction);
    }

    @Override
    public void onDisable() {
        dispatch(onDisableAction);
    }

    @Override
    public void onRender2D(GuiGraphics context, float delta) {
        if (onRender2DAction == null || !isToggled()) return;
        try {
            onRender2DAction.accept(context, delta);
        } catch (Throwable t) {
            handleError(t);
        }
    }

    // --- Safe Dispatch ---

    /**
     * Любая ошибка в колбэке скрипта ловится здесь: она не должна ломать клиент.
     * После MAX_ERRORS ошибок модуль автоматически выключается.
     */
    private void handleError(Throwable t) {
        errorCount++;
        int reported = scriptContext != null ? scriptContext.reportError(getName(), t) : errorCount;

        if ((errorCount >= ScriptContext.MAX_ERRORS || reported >= ScriptContext.MAX_ERRORS) && isToggled()) {
            setToggled(false);
            if (scriptContext != null) {
                ChatHelper.sendMessage(Component.literal("§c[Script] §f" + scriptContext.scriptName
                        + "§7: модуль §e" + getName() + " §7выключен после " + ScriptContext.MAX_ERRORS + " ошибок."));
            }
        }
    }

    private void dispatch(Consumer<ScriptModule> action) {
        if (action == null) return;
        try {
            action.accept(this);
        } catch (Throwable t) {
            handleError(t);
        }
    }

    private <T> void dispatchEvent(Consumer<T> action, T event) {
        if (action == null) return;
        try {
            action.accept(event);
        } catch (Throwable t) {
            handleError(t);
        }
    }

    // --- Internal Event Dispatchers ---

    @EventHandler
    private void handleTick(TickEvent.Pre event) {
        if (isToggled()) dispatch(onTickAction);
    }

    @EventHandler
    private void handleTickPost(TickEvent.Post event) {
        if (isToggled()) dispatch(onTickPostAction);
    }

    @EventHandler
    private void handleSend(PacketEvent.Send event) {
        if (isToggled()) dispatchEvent(onPacketSendAction, event);
    }

    @EventHandler
    private void handleReceive(PacketEvent.Receive event) {
        if (isToggled()) dispatchEvent(onPacketReceiveAction, event);
    }

    @EventHandler
    private void handleRender3D(Render3DEvent event) {
        if (isToggled()) dispatchEvent(onRender3DAction, event);
    }

    @EventHandler
    private void handlePlayerMove(PlayerMoveEvent event) {
        if (isToggled()) dispatchEvent(onPlayerMoveAction, event);
    }

    @EventHandler
    private void handleBreakBlock(StartBreakingBlockEvent event) {
        if (isToggled()) dispatchEvent(onBreakBlockAction, event);
    }

    @EventHandler
    private void handleSendMessage(SendMessageEvent event) {
        if (isToggled()) dispatchEvent(onSendMessageAction, event);
    }

    @EventHandler
    private void handleMouseClick(MouseClickEvent event) {
        if (isToggled()) dispatchEvent(onMouseClickAction, event);
    }

    @EventHandler
    private void handleKeyInput(KeyInputEvent event) {
        if (isToggled()) dispatchEvent(onKeyInputAction, event);
    }

    @EventHandler
    private void handleInteractEntity(InteractEntityEvent event) {
        if (isToggled()) dispatchEvent(onInteractEntityAction, event);
    }
}