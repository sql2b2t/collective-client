package me.collective.scripting

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.events.ClientPacketListenerSendChatEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.HudRenderEvent
import me.collective.client.events.InputPressEvent
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MinecraftTickEvent
import me.collective.client.events.MultiPlayerGameModeStartDestroyBlockEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.PlayerAttackEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.StringSetting
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import java.awt.Color
import java.util.function.BiConsumer
import java.util.function.Consumer

class ScriptModule(name: String, description: String, category: ModuleCategory) : Module(name, description, category) {
    private var scriptContext: ScriptContext? = null
    private var errorCount: Int = 0

    private var onEnableAction: Consumer<ScriptModule>? = null
    private var onDisableAction: Consumer<ScriptModule>? = null
    private var onTickAction: Consumer<ScriptModule>? = null
    private var onTickPostAction: Consumer<ScriptModule>? = null
    private var onPacketSendAction: Consumer<PacketEvent.Send>? = null
    private var onPacketReceiveAction: Consumer<PacketEvent.Receive>? = null
    private var onRender3DAction: Consumer<WorldRenderEvent.BeforeBufferSource>? = null
    private var onRender2DAction: BiConsumer<GuiGraphicsExtractor, Float>? = null
    private var onPlayerMoveAction: Consumer<LocalPlayerMoveEvent>? = null
    private var onBreakBlockAction: Consumer<MultiPlayerGameModeStartDestroyBlockEvent>? = null
    private var onSendMessageAction: Consumer<ClientPacketListenerSendChatEvent>? = null
    private var onMouseClickAction: Consumer<InputPressEvent>? = null
    private var onKeyInputAction: Consumer<InputPressEvent>? = null
    private var onInteractEntityAction: Consumer<PlayerAttackEvent>? = null

    /** Привязка к скрипту-владельцу (для отчётов об ошибках). */
    fun setScriptContext(ctx: ScriptContext) {
        this.scriptContext = ctx
    }

    // --- Fluent Settings API ---

    fun boolSetting(name: String, defaultValue: Boolean): BooleanSetting {
        return add(BooleanSetting(name, defaultValue))
    }

    fun numberSetting(name: String, defaultValue: Double, min: Double, max: Double, step: Double): NumberSetting<Double> {
        return add(NumberSetting(name, defaultValue, min, max))
    }

    fun colorSetting(name: String, defaultValue: Color): ColorSetting {
        return add(ColorSetting(name, defaultValue))
    }

    fun stringSetting(name: String, defaultValue: String): StringSetting {
        return add(StringSetting(name, defaultValue))
    }

    // --- Event Subscriptions ---

    fun onEnable(action: Consumer<ScriptModule>): ScriptModule {
        this.onEnableAction = action
        return this
    }

    fun onDisable(action: Consumer<ScriptModule>): ScriptModule {
        this.onDisableAction = action
        return this
    }

    fun onTick(action: Consumer<ScriptModule>): ScriptModule {
        this.onTickAction = action
        return this
    }

    fun onTickPost(action: Consumer<ScriptModule>): ScriptModule {
        this.onTickPostAction = action
        return this
    }

    fun onPacketSend(action: Consumer<PacketEvent.Send>): ScriptModule {
        this.onPacketSendAction = action
        return this
    }

    fun onPacketReceive(action: Consumer<PacketEvent.Receive>): ScriptModule {
        this.onPacketReceiveAction = action
        return this
    }

    fun onRender3D(action: Consumer<WorldRenderEvent.BeforeBufferSource>): ScriptModule {
        this.onRender3DAction = action
        return this
    }

    fun onRender2D(action: BiConsumer<GuiGraphicsExtractor, Float>): ScriptModule {
        this.onRender2DAction = action
        return this
    }

    fun onPlayerMove(action: Consumer<LocalPlayerMoveEvent>): ScriptModule {
        this.onPlayerMoveAction = action
        return this
    }

    fun onBreakBlock(action: Consumer<MultiPlayerGameModeStartDestroyBlockEvent>): ScriptModule {
        this.onBreakBlockAction = action
        return this
    }

    fun onSendMessage(action: Consumer<ClientPacketListenerSendChatEvent>): ScriptModule {
        this.onSendMessageAction = action
        return this
    }

    fun onMouseClick(action: Consumer<InputPressEvent>): ScriptModule {
        this.onMouseClickAction = action
        return this
    }

    fun onKeyInput(action: Consumer<InputPressEvent>): ScriptModule {
        this.onKeyInputAction = action
        return this
    }

    fun onInteractEntity(action: Consumer<PlayerAttackEvent>): ScriptModule {
        this.onInteractEntityAction = action
        return this
    }

    // --- Module Lifecycle ---

    override fun onEnable() {
        dispatch(onEnableAction)
    }

    override fun onDisable() {
        dispatch(onDisableAction)
    }

    // --- Safe Dispatch ---

    /**
     * Любая ошибка в колбэке скрипта ловится здесь: она не должна ломать клиент.
     * После MAX_ERRORS ошибок модуль автоматически выключается.
     */
    private fun handleError(t: Throwable) {
        errorCount++
        val reported = scriptContext?.reportError(name, t) ?: errorCount

        if ((errorCount >= ScriptContext.MAX_ERRORS || reported >= ScriptContext.MAX_ERRORS) && isToggled()) {
            setToggled(false)
            val ctx = scriptContext
            if (ctx != null) {
                ChatUtils.printChat(
                    Component.literal(
                        "§c[Script] §f" + ctx.scriptName +
                            "§7: модуль §e" + name + " §7выключен после " + ScriptContext.MAX_ERRORS + " ошибок."
                    ),
                    ChatUtils.createSignature(-1)
                )
            }
        }
    }

    private fun dispatch(action: Consumer<ScriptModule>?) {
        if (action == null) return
        try {
            action.accept(this)
        } catch (t: Throwable) {
            handleError(t)
        }
    }

    private fun <T> dispatchEvent(action: Consumer<T>?, event: T) {
        if (action == null) return
        try {
            action.accept(event)
        } catch (t: Throwable) {
            handleError(t)
        }
    }

    // --- Internal Event Dispatchers ---

    @EventHandler
    private fun handleTick(event: MinecraftTickEvent) {
        if (isToggled()) dispatch(onTickAction)
    }

    @EventHandler
    private fun handleTickPost(event: LocalPlayerTickEvent) {
        if (isToggled()) dispatch(onTickPostAction)
    }

    @EventHandler
    private fun handleSend(event: PacketEvent.Send) {
        if (isToggled()) dispatchEvent(onPacketSendAction, event)
    }

    @EventHandler
    private fun handleReceive(event: PacketEvent.Receive) {
        if (isToggled()) dispatchEvent(onPacketReceiveAction, event)
    }

    @EventHandler
    private fun handleRender3D(event: WorldRenderEvent.BeforeBufferSource) {
        if (isToggled()) dispatchEvent(onRender3DAction, event)
    }

    @EventHandler
    private fun handleRender2D(event: HudRenderEvent) {
        if (isToggled() && onRender2DAction != null) {
            try {
                val graphics = event.guiGraphics ?: return
                onRender2DAction!!.accept(graphics, event.partialTick)
            } catch (t: Throwable) {
                handleError(t)
            }
        }
    }

    @EventHandler
    private fun handlePlayerMove(event: LocalPlayerMoveEvent) {
        if (isToggled()) dispatchEvent(onPlayerMoveAction, event)
    }

    @EventHandler
    private fun handleBreakBlock(event: MultiPlayerGameModeStartDestroyBlockEvent) {
        if (isToggled()) dispatchEvent(onBreakBlockAction, event)
    }

    @EventHandler
    private fun handleSendMessage(event: ClientPacketListenerSendChatEvent) {
        if (isToggled()) dispatchEvent(onSendMessageAction, event)
    }

    @EventHandler
    private fun handleMouseClick(event: InputPressEvent) {
        if (isToggled() && event.isMouse) dispatchEvent(onMouseClickAction, event)
    }

    @EventHandler
    private fun handleKeyInput(event: InputPressEvent) {
        if (isToggled() && !event.isMouse) dispatchEvent(onKeyInputAction, event)
    }

    @EventHandler
    private fun handleInteractEntity(event: PlayerAttackEvent) {
        if (isToggled()) dispatchEvent(onInteractEntityAction, event)
    }
}
