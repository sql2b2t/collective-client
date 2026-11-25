package me.collective.client.modules.client

import me.collective.client.utils.collections.NamedIdentifier

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.events.MinecraftTickEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.gui.screens.ClickGuiScreen
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.gui.CursorType
import me.collective.client.utils.world.Season
import me.collective.client.audio.BuiltinSound
import me.collective.client.CollectiveClient
import me.collective.client.modules.Keybind
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.IdentifierSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import java.awt.Color
import java.io.InputStream
import java.time.LocalDate
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function
import java.util.function.Predicate
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import org.lwjgl.glfw.GLFW

class UIModule : Module("UI", "Displays the client's click gui.", ModuleCategory.CLIENT) {
    private val panelResetPending = AtomicBoolean(false)
    private val resetSetting: Setting<Boolean>
    @JvmField
    val styleSetting: Setting<StyleChoice>
    @JvmField
    val animSpeedSetting: Setting<Float>
    @JvmField
    val scaleSetting: Setting<Float>
    @JvmField
    val snowSetting: Setting<Boolean>
    @JvmField
    val gamesSetting: Setting<Boolean>
    @JvmField
    val descriptionsSetting: Setting<Boolean>
    @JvmField
    val languageSetting: Setting<LanguageChoice>
    @JvmField
    val settingsSetting: Setting<Boolean>
    @JvmField
    val elementsSetting: Setting<Boolean>
    @JvmField
    val aeroSetting: Setting<Boolean>
    @JvmField
    val elementGradientSetting: Setting<Boolean>
    @JvmField
    val bindsSetting: Setting<Boolean>
    @JvmField
    val gearSetting: Setting<Boolean>
    @JvmField
    val soundsSetting: Setting<Boolean>
    @JvmField
    val hoverSetting: Setting<Boolean>
    @JvmField
    val hoverSoundSetting: Setting<NamedIdentifier>
    @JvmField
    val hoverVolumeSetting: Setting<Float>
    @JvmField
    val leftClickSetting: Setting<Boolean>
    @JvmField
    val leftClickSoundSetting: Setting<NamedIdentifier>
    @JvmField
    val leftClickVolumeSetting: Setting<Float>
    @JvmField
    val rightClickSetting: Setting<Boolean>
    @JvmField
    val rightClickSoundSetting: Setting<NamedIdentifier>
    @JvmField
    val rightClickVolumeSetting: Setting<Float>
    @JvmField
    val textSetting: Setting<Boolean>
    @JvmField
    val textColorSetting: Setting<Color>
    @JvmField
    val enabledColorSetting: Setting<Color>
    private val colorsSetting: Setting<Boolean>
    @JvmField
    val colorSetting: Setting<Color>
    @JvmField
    val backgroundSetting: Setting<Color>
    @JvmField
    val buttonSetting: Setting<Color>
    @JvmField
    val enabledSetting: Setting<Color>
    @JvmField
    val tintSetting: Setting<Color>
    @JvmField
    val gradientColorSetting: Setting<Color>

    override fun onEnable() {
        if (this.isWorldUnavailable() || ClientAccess.collectiveMinecraft.screen is ClickGuiScreen) {
            return
        }
        val openTask = Runnable {
            val screen = ClientContext.getClickGui()
            screen.open()
        }
        if (ClientAccess.collectiveMinecraft.screen is ChatScreen) {
            val guiScheduler = ClientContext.taskScheduler
            guiScheduler.scheduleRotationTask(openTask, 0)
        } else {
            openTask.run()
        }
    }

    override fun getKeybind(): Keybind {
        var k = super.getKeybind()
        if (k.mode != Keybind.Mode.TOGGLE) {
            k = k.withMode(Keybind.Mode.TOGGLE)
            super.setKeybind(k)
        }
        return k
    }

    override fun setKeybind(keybind: Keybind) {
        super.setKeybind(keybind)
    }

    override fun onDisable() {
        val currentScreen = ClientAccess.collectiveMinecraft.screen
        if (currentScreen is ClickGuiScreen) {
            ClientContext.getClickGui()
                .closeWithTransition()
        }
    }

    @EventHandler
    private fun onMinecraftTick(event: MinecraftTickEvent) {
        val minecraft = ClientAccess.collectiveMinecraft
        val window = minecraft.window
        val windowHandle = window.window
        if (GLFW.glfwGetMouseButton(windowHandle, 0) == 1) {
            return
        }
        val pendingReset = this.panelResetPending
        if (!pendingReset.get()) {
            return
        }
        val screen = ClientContext.getClickGui()
        screen.resetPanelPositions()
        this.resetSetting.value = true
        this.panelResetPending.set(false)
    }

    @EventHandler
    private fun onLocalPlayerTick(event: LocalPlayerTickEvent) {
        if (ClientAccess.collectiveMinecraft.screen !is ClientScreen) {
            this.toggle()
        }
    }

    @EventHandler
    private fun onPacketReceive(
        event: PacketEvent.Receive
    ) {
        if (event.packet is ClientboundDisconnectPacket) {
            this.disable()
        }
    }

    fun getAnimationSpeed(): Float {
        return this.animSpeedSetting.value
    }

    init {
        this.resetSetting = this.add(BooleanSetting(("Reset"), true))
        this.styleSetting = this.add(EnumSetting(("Style"), StyleChoice.PLAIN))
        this.animSpeedSetting = this.add(NumberSetting("AnimSpeed", 1.0f, 0.5f, 2.5f))
        this.scaleSetting = this.add(NumberSetting("Scale", 1.0f, 0.1f, 2.0f))
        this.snowSetting = this.add(BooleanSetting("Snow", true))
        this.gamesSetting = this.add(BooleanSetting("Games", true))
        val descriptionsOption = BooleanSetting("Descriptions", true)
        descriptionsOption.markVisibilityDependency()
        this.descriptionsSetting = this.add(descriptionsOption)
        this.languageSetting = this.add(EnumSetting("Language", LanguageChoice.ENGLISH)
            .dependsOn(this.descriptionsSetting))
        this.settingsSetting = this.add(BooleanSetting("Settings", true)
            .dependsOn(this.descriptionsSetting))
        val elementsOption = BooleanSetting("Elements", true)
        elementsOption.markAsGroup()
        elementsOption.markVisibilityDependency()
        this.elementsSetting = this.add(elementsOption)
        this.aeroSetting = this.add(BooleanSetting("Aero", true)
            .dependsOn(this.elementsSetting))
        this.elementGradientSetting = this.add(BooleanSetting("Gradient", true)
            .dependsOn(this.elementsSetting))
        val bindsVisibility = Predicate<Any?> { ignored -> this.elementsSetting.isExpanded }
        this.bindsSetting = this.add(BooleanSetting("Binds", false, bindsVisibility))
        val gearVisibility = Predicate<Any?> { ignored -> this.elementsSetting.isExpanded }
        this.gearSetting = this.add(BooleanSetting("Gear", true, gearVisibility))
        val soundsOption = BooleanSetting("Sounds", true)
        soundsOption.markAsGroup()
        soundsOption.markVisibilityDependency()
        this.soundsSetting = this.add(soundsOption)
        val hoverVisibility = Predicate<Any?> { ignored -> this.soundsSetting.isExpanded }
        val hoverOption = BooleanSetting("Hover", true, hoverVisibility)
        hoverOption.markVisibilityDependency()
        this.hoverSetting = this.add(hoverOption)
        val hoverSoundKey = "Sound"
        this.hoverSoundSetting = this.add(IdentifierSetting(hoverSoundKey, BuiltinSound.HOVER.identifier)
            .dependsOn(this.soundsSetting, this.hoverSetting))
        val hoverVolumeOption = NumberSetting("Volume", 0.5f, 0.0f, 1.0f)
        this.hoverVolumeSetting = this.add(hoverVolumeOption.dependsOn(this.soundsSetting, this.hoverSetting))
        val leftClickVisibility = Predicate<Any?> { ignored -> this.soundsSetting.isExpanded }
        val leftClickOption = BooleanSetting("LeftClick", true, leftClickVisibility)
        leftClickOption.markVisibilityDependency()
        this.leftClickSetting = this.add(leftClickOption)
        this.leftClickSoundSetting = this.add(IdentifierSetting("Sound", BuiltinSound.CLICK.identifier)
            .dependsOn(this.soundsSetting, this.leftClickSetting))
        val leftClickVolumeOption = NumberSetting("Volume", 0.5f, 0.0f, 1.0f)
        this.leftClickVolumeSetting = this.add(leftClickVolumeOption.dependsOn(this.soundsSetting, this.leftClickSetting))
        val rightClickVisibility = Predicate<Any?> { ignored -> this.soundsSetting.isExpanded }
        val rightClickOption = BooleanSetting("RightClick", true, rightClickVisibility)
        rightClickOption.markVisibilityDependency()
        this.rightClickSetting = this.add(rightClickOption)
        this.rightClickSoundSetting = this.add(IdentifierSetting("Sound", BuiltinSound.RCLICK.identifier)
            .dependsOn(this.soundsSetting, this.rightClickSetting))
        val rightClickVolumeOption = NumberSetting("Volume", 0.5f, 0.0f, 1.0f)
        this.rightClickVolumeSetting = this.add(rightClickVolumeOption.dependsOn(this.soundsSetting, this.rightClickSetting))
        val textOption = BooleanSetting("Text", false)
        textOption.markAsGroup()
        textOption.markVisibilityDependency()
        this.textSetting = this.add(textOption)
        val textColorKey = "TextColor"
        this.textColorSetting = this.add(ColorSetting(textColorKey, Color(-1973791, true))
            .dependsOn(this.textSetting))
        this.enabledColorSetting = this.add(ColorSetting("EnabledColor", Color(-1, true))
            .dependsOn(this.textSetting))
        val colorsOption = BooleanSetting("Colors", false)
        colorsOption.markAsGroup()
        colorsOption.markVisibilityDependency()
        this.colorsSetting = this.add(colorsOption)
        val colorVisibility = Predicate<Any?> { ignored -> this.colorsSetting.isExpanded }
        this.colorSetting = this.add(ColorSetting("Color", Color(-2100849420, true), colorVisibility))
        val backgroundVisibility = Predicate<Any?> { ignored -> this.colorsSetting.isExpanded }
        this.backgroundSetting = this.add(ColorSetting("Background", Color(516396276, true), backgroundVisibility))
        val buttonVisibility = Predicate<Any?> { ignored -> this.colorsSetting.isExpanded }
        this.buttonSetting = this.add(ColorSetting("Button", Color(1942459636, true), buttonVisibility))
        val enabledVisibility = Predicate<Any?> { ignored -> this.colorsSetting.isExpanded }
        this.enabledSetting = this.add(ColorSetting("Enabled", Color(1523029236, true), enabledVisibility))
        val tintVisibility = Predicate<Any?> { ignored -> this.colorsSetting.isExpanded }
        this.tintSetting = this.add(ColorSetting("Tint", Color(1679822128, true), tintVisibility))
        val gradientColorVisibility = Predicate<Any?> { ignored -> this.colorsSetting.isExpanded }
        this.gradientColorSetting = this.add(ColorSetting("Gradient", Color(264738036, true), gradientColorVisibility))
        val eventOwner = UIModule::class.java
        val eventBus = ClientAccess.eventBus
        eventBus.subscribeStatic(eventOwner)

        this.ignoreState()

        this.setDrawn(false)
        val keybindTransform = Function<Keybind, Keybind> { keybind ->
            keybind.withCode(344)
        }
        this.modifyKeybind(keybindTransform)
        INSTANCE = this
        val gamesCallbackSetting: Setting<Boolean> = this.gamesSetting
        val gamesChangeCallback = Runnable {
            var screen = ClientContext.clickGui

            if (!screen.isGamePanelUnlocked()) {
                screen = ClientContext.clickGui

                val gamePanels: ArrayList<me.collective.client.gui.panels.Panel> = screen.panels
                val gamePanelPredicate = Predicate<me.collective.client.gui.panels.Panel> { panel ->
                    panel.name == "Game"
                }
                gamePanels.removeIf(gamePanelPredicate)
                val games: Setting<Boolean> = this.gamesSetting
                if (games.value) {
                    screen = ClientContext.clickGui

                    screen.addGamePanel()
                }
            }
        }
        gamesCallbackSetting.setChangeCallback(gamesChangeCallback)
        val resetCallbackSetting: Setting<Boolean> = this.resetSetting
        val resetChangeCallback = Runnable {
            val reset: Setting<Boolean> = this.resetSetting
            if (!(reset.value)) {
                this.resetSetting.value = true
                if (ClientContext.clickGui != null) {
                    val screen = ClientContext.getClickGui()

                    screen.resetPanelPositions()
                }
            }
        }
        resetCallbackSetting.setChangeCallback(resetChangeCallback)
        val styleCallbackSetting: Setting<StyleChoice> = this.styleSetting
        val styleChangeCallback = Runnable { this.panelResetPending.set(true) }
        styleCallbackSetting.setChangeCallback(styleChangeCallback)
        val languageCallbackSetting: Setting<LanguageChoice> = this.languageSetting
        val languageChangeCallback = Runnable { applyLanguageSetting() }
        languageCallbackSetting.setChangeCallback(languageChangeCallback)
        this.hoverSoundSetting.setConfigName("HoverSound")
        this.hoverVolumeSetting.setConfigName("HoverVolume")
        this.leftClickSoundSetting.setConfigName("LeftClickSound")
        this.leftClickVolumeSetting.setConfigName("LeftClickVolume")
        this.rightClickSoundSetting.setConfigName("RightClickSound")
        this.rightClickVolumeSetting.setConfigName("RightClickVolume")
        this.backgroundSetting.setConfigName("BgColor")
        this.buttonSetting.setConfigName("BgButton")
        this.enabledSetting.setConfigName("BgEnabled")
        this.gradientColorSetting.setConfigName("GradientColor")
        val winterCapture = Season.WINTER
        val today = LocalDate.now()
        val month = today.monthValue
        if (!winterCapture.containsMonth(month)) {
            this.snowSetting.value = false
            this.unregister(this.snowSetting)
        }
    }

    enum class LanguageChoice(private val displayName: String, private val languageCode: String) : NamedEntry {
        ENGLISH("English", "en_us"),
        RUSSIAN("Русский", "ru_ru"),
        CHINESE("中文", "zh_cn"),
        SPANISH("Español", "es_es"),
        GERMAN("Deutsch", "de_de"),
        FRENCH("Français", "fr_fr"),
        PORTUGUESE("Português", "pt_br"),
        UKRAINIAN("Українська", "uk_ua"),
        POLISH("Polski", "pl_pl"),
        JAPANESE("日本語", "ja_jp");

        fun openStream(): InputStream? {
            return CollectiveClient::class.java.getResourceAsStream("/assets/collective/lang/$languageCode.ini")
        }

        override fun getName(): String {
            return this.displayName
        }
    }

    enum class StyleChoice(private val displayName: String) : NamedEntry {
        PLAIN("Plain"),
        SLIM("Slim");

        fun getHeightPadding(): Int {
            return if (this == SLIM) 3 else 4
        }

        val heightPadding: Int
            @JvmName("getHeightPaddingProp") get() = getHeightPadding()

        fun getWidthPadding(): Int {
            return if (this == SLIM) 4 else 7
        }

        val widthPadding: Int
            @JvmName("getWidthPaddingProp") get() = getWidthPadding()

        override fun getName(): String {
            return this.displayName
        }
    }

    companion object {
        // Java-поле UIModule.INSTANCE: статическое поле типа UIModule, читается из Java напрямую.
        // Для Kotlin объявлено non-null (ChatInputOverlay.kt, FontsModule.kt вызывают
        // UIModule.INSTANCE.xxx). Инициализатор через erased nullValue() кладёт в поле null
        // без проверки компилятора — до присвоения в конструкторе разыменование даёт NPE,
        // как в исходном Java (поле было null до регистрации модуля).
        @JvmField
        var INSTANCE: UIModule = nullValue()

        @Suppress("UNCHECKED_CAST")
        private fun <T> nullValue(): T = null as T

        /** Перезагружает таблицу переводов по выбранному в настройках языку. */
        @JvmStatic
        fun applyLanguageSetting() {
            val tableCapture = ClientContext.languageTable
            val ui = ClientContext.getModule(UIModule::class.java)
            if (ui == null || tableCapture == null) return

            val selectedLanguage = ui.languageSetting.value
            val languageStream = selectedLanguage.openStream()
            tableCapture.load(languageStream)
            if ((selectedLanguage == LanguageChoice.CHINESE || selectedLanguage == LanguageChoice.JAPANESE) &&
         FontsModule.INSTANCE!!.isToggled()
            ) {
                FontsModule.INSTANCE!!.validateFont(false)
            }
        }

        @JvmStatic
        @EventHandler
        private fun onCursorTick(event: MinecraftTickEvent) {
            if (ClientAccess.collectiveMinecraft.screen !is ClientScreen) {
                val cursor = CursorType.STANDARD
                cursor.apply()
            }
        }
    }
}
