package me.collective.client.modules.client


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.HudRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.gui.screens.ClickGuiScreen
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.gui.screens.HudEditorScreen
import me.collective.client.render.GuiDrawUtils
import me.collective.client.utils.InterpolatedFloat
import me.collective.client.render.FontRenderer
import me.collective.client.render.HudWidgetRenderer
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.render.NoRenderModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import java.awt.Color
import java.util.function.Predicate
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.phys.Vec3

class HUDModule : Module("HUD", "Draws useful HUD elements on your screen.", ModuleCategory.CLIENT) {
    private val hudEditorVisible: Predicate<Any?> = Predicate {
        val screen = ClientAccess.collectiveMinecraft.gui.screen()
        if (screen is ClientScreen) {
            screen.parentScreen == null
        } else {
            true
        }
    }
    private val hudEditorSetting: Setting<Boolean>
    @JvmField
    val iconsSetting: Setting<EffectIconMode>
    @JvmField
    val textScaleSetting: Setting<Float>
    @JvmField
    val smoothWidthSetting: Setting<Boolean>
    @JvmField
    val parallaxSetting: Setting<Boolean>
    @JvmField
    val bloomSetting: Setting<Boolean>
    @JvmField
    val bloomShadowSetting: Setting<Boolean>
    @JvmField
    val bloomRadiusSetting: Setting<Int>
    @JvmField
    val bloomStrengthSetting: Setting<Float>
    private val safeZonesSetting: Setting<Boolean>
    private val safeXSetting: Setting<Int>
    private val safeYSetting: Setting<Int>
    private val colorsSetting: Setting<Boolean>
    @JvmField
    val fadeSetting: Setting<HudColorMode>
    // Java-поля были protected; HudColorMode.kt (тот же пакет) читает их напрямую.
    val fadeOffsetSetting: Setting<Int>
    val colorAmountSetting: Setting<Int>
    val fadeSpeedSetting: Setting<Int>
    val mixColorsSetting: Setting<Boolean>
    @JvmField
    val colorSetting: Setting<Color>
    @JvmField
    val secondColorSetting: Setting<Color>
    // Java-поле было package-private; HudColorMode.kt читает напрямую.
    val rawColorSettings: MutableList<Setting<Color>>
    private val effectIconOffset: InterpolatedFloat
    private val clickGuiSearchOffset: InterpolatedFloat
    // Java-поля были package-private; ChatInputOverlay.kt читает напрямую.
    val paddedChatInputHeight: InterpolatedFloat
    val chatInputHeight: InterpolatedFloat
    private val chatInputOverlay: ChatInputOverlay

    var safeX: Int
        @JvmName("getSafeXProp") get() = getSafeX()
        @JvmName("setSafeXProp") set(value) = setSafeX(value)

    var safeY: Int
        @JvmName("getSafeYProp") get() = getSafeY()
        @JvmName("setSafeYProp") set(value) = setSafeY(value)

    fun getSafeX(): Int {
        return this.safeXSetting.value
    }

    fun updateChatInputHeight(height: Float) {
        this.paddedChatInputHeight.setTarget(if (height > 0.0f) height + 3.0f else 0.0f)
        this.paddedChatInputHeight.updateAndGet()
        this.chatInputHeight.setTarget(height)
        this.chatInputHeight.updateAndGet()
    }

    fun getTextScale(): Float {
        return this.textScaleSetting.value
    }

    fun getSafeY(): Int {
        return this.safeYSetting.value
    }

    fun setSafeY(value: Int) {
        val setting: Setting<Int> = this.safeYSetting
        setting.value = value
    }

    fun getBottomHudOffset(): Float {
        val chatOffset = this.getChatInputOffset()
        val searchOffset = this.clickGuiSearchOffset.currentValue
        return maxOf(chatOffset, searchOffset)
    }

    fun getEffectIconOffset(): Float {
        return this.effectIconOffset.currentValue
    }

    fun setSafeX(value: Int) {
        val setting: Setting<Int> = this.safeXSetting
        setting.value = value
    }

    @EventHandler
    private fun onHudRender(event: HudRenderEvent) {
        var effectRows = 0
        val iconModeSetting: Setting<EffectIconMode> = this.iconsSetting
        if (iconModeSetting.value == EffectIconMode.MOVE &&
         ClientAccess.collectiveMinecraft.gui.screen() !is InventoryScreen
        ) {
            val player = ClientAccess.collectiveMinecraft.player!!
            if (player != null) {
                val activeEffects = player.activeEffects
                val effects = activeEffects.iterator()

                while (effects.hasNext()) {
                    val effectInstance = effects.next() as MobEffectInstance
                    val effectHolder = effectInstance.effect
                    val noRender = noRenderModule
                    if (noRender != null && noRender.isToggled()) {
                        val darknessSetting: Setting<Boolean> = noRender.darknessSetting
                        val hideDarkness = darknessSetting.value
                        if (hideDarkness && effectHolder == MobEffects.DARKNESS) {
                            continue
                        }
                    }

                    if (effectRows == 0) {
                        effectRows++
                    }

                    val classifiedEffectHolder = effectInstance.effect
                    val effect = classifiedEffectHolder.value() as MobEffect
                    if (!effect.isBeneficial) {
                        effectRows = 2
                    }
                }
            }
        }

        val activeScreen = ClientAccess.collectiveMinecraft.gui.screen()
        if (activeScreen != null && activeScreen.showsActiveEffects()) {
            effectRows = 0
        }

        var moveForSearch = false
        var searchHeightBase = FontRenderer.INSTANCE
            .height
        searchHeightBase -= this.getSafeY()

        val searchHeight = searchHeightBase + 2
        if (ClientAccess.collectiveMinecraft.gui.screen() is ClickGuiScreen) {
            val clickGui = ClientAccess.collectiveMinecraft.gui.screen() as ClickGuiScreen
            val searchBox = clickGui.searchBox
            moveForSearch = searchBox?.isFocused != true
        }

        val searchTargetOffset = if (moveForSearch) searchHeight.toFloat() else 0.0f
        this.clickGuiSearchOffset.setTarget(searchTargetOffset)
        this.clickGuiSearchOffset.updateAndGet()
        val capturedEffectAnimation = this.effectIconOffset

        val effectTargetOffset = (25 * effectRows).toFloat()
        capturedEffectAnimation.setTarget(effectTargetOffset)
        this.effectIconOffset.updateAndGet()
        HudWidgetRenderer.renderingHud = true
        try {
            if (ClientAccess.collectiveMinecraft.gui.screen() !is HudEditorScreen) {
                val editor = ClientContext.getHudEditor()
                val poseStack = event.poseStack ?: return
                editor.renderWidgets(poseStack, -999.0, -999.0)
                GuiDrawUtils.flush()
            }
        } finally {
            HudWidgetRenderer.renderingHud = false
        }
    }

    fun shouldDrawTextShadow(): Boolean {
        return !this.bloomSetting.value || this.bloomShadowSetting.value
    }

    fun getChatInputOffset(): Float {
        return this.paddedChatInputHeight.getValue()
    }

    fun getParallaxOffset(): Vec3 {
        val player = ClientAccess.collectiveMinecraft.player!!
        if (!this.parallaxSetting.value || player == null || player.isSpectator) return Vec3.ZERO
        val partialTick = ClientAccess.collectiveMinecraft.deltaTracker.getGameTimeDeltaPartialTick(false)
        val pitchBob = Mth.lerp(partialTick, player.xBobO, player.xBob)
        val yawBob = Mth.lerp(partialTick, player.yBobO, player.yBob)
        return Vec3(
            (-(player.getViewYRot(partialTick) - yawBob) * 0.1f).toDouble(),
            (-(player.getViewXRot(partialTick) - pitchBob) * 0.1f).toDouble(),
            0.0
        )
    }

    init {
        val editorEnabled = true
        val editorSetting = BooleanSetting(
            "HudEditor", editorEnabled, this.hudEditorVisible
        )
        this.hudEditorSetting = this.add(editorSetting)

        val iconsOption = EnumSetting<EffectIconMode>("Icons", EffectIconMode.MOVE)
        this.iconsSetting = this.add(iconsOption)

        val scaleSetting = NumberSetting(
            "TextScale",
            (1.0f),
            (0.5f),
            2.0f
        )
        this.textScaleSetting = this.add(scaleSetting)

        val smoothWidthOption = BooleanSetting("SmoothWidth", (false))
        this.smoothWidthSetting = this.add(smoothWidthOption)

        val parallaxOption = BooleanSetting("Parallax", (false))
        this.parallaxSetting = this.add(parallaxOption)
        this.bloomSetting = this.add(BooleanSetting("Bloom", false).markVisibilityDependency())
        this.bloomShadowSetting = this.add(BooleanSetting("Shadow", false).dependsOn(this.bloomSetting))
        this.bloomRadiusSetting = this.add(NumberSetting<Int>("Radius", 6, 2, 8).dependsOn(this.bloomSetting))
        this.bloomStrengthSetting = this.add(NumberSetting("Strength", 1.0f, 0.5f, 1.5f).dependsOn(this.bloomSetting))

        val safeZonesOption = BooleanSetting("SafeZones", (false))
        val safeZonesGroup: Setting<Boolean> = safeZonesOption.markAsGroup()
        this.safeZonesSetting = this.add(safeZonesGroup)

        val safeXOption = NumberSetting<Int>(
            "SafeX", 1, 0, 20
        )
        this.safeXSetting = this.add(safeXOption.dependsOn(this.safeZonesSetting))

        val safeYOption = NumberSetting<Int>(
            "SafeY", 1, 0, 10
        )
        this.safeYSetting = this.add(safeYOption.dependsOn(this.safeZonesSetting))

        val colorsOption = BooleanSetting("Colors", (true))
        val colorsGroup: Setting<Boolean> = colorsOption.markAsGroup()
        this.colorsSetting = this.add(colorsGroup)

        val fadeOption = EnumSetting("Fade", HudColorMode.NONE, Predicate<HudColorMode> { ignored ->
            val fadeGroup: Setting<Boolean> = this.colorsSetting
            fadeGroup.isExpanded
        })
        this.fadeSetting = this.add(fadeOption)

        val fadeOffsetOption = NumberSetting<Int>(
            "FadeOffset", (5), 1, 30
        )
        this.fadeOffsetSetting = this.add(fadeOffsetOption.dependsOn(this.colorsSetting))

        val colorAmountOption = NumberSetting<Int>(
            "ColorAmount",
            (5),
            (2),
            9,
            Predicate<Int> { ignored ->
                val amountVisibilityModeSetting: Setting<HudColorMode> = this.fadeSetting
                amountVisibilityModeSetting.value == HudColorMode.RAW &&
         this.colorsSetting.isExpanded
            }
        )
        this.colorAmountSetting = this.add(colorAmountOption)

        val fadeSpeedOption = NumberSetting<Int>(
            "FadeSpeed",
            (50),
            (20),
            100,
            Predicate<Int> { ignored ->
                val speedVisibilityModeSetting: Setting<HudColorMode> = this.fadeSetting
                speedVisibilityModeSetting.value == HudColorMode.RAW &&
         this.colorsSetting.isExpanded
            }
        )
        this.fadeSpeedSetting = this.add(fadeSpeedOption)

        val mixColorsEnabled = true
        val mixColorsOption = BooleanSetting("MixColors", mixColorsEnabled, Predicate<Boolean> { ignored ->
            val mixVisibilityModeSetting: Setting<HudColorMode> = this.fadeSetting
            mixVisibilityModeSetting.value == HudColorMode.RAW &&
         this.colorsSetting.isExpanded
        })
        this.mixColorsSetting = this.add(mixColorsOption)

        val primaryColor = ColorSetting("Color", Color(
            189,
            153,
            255,
            255
        ), Predicate<Color> { ignored ->
            val primaryVisibilityModeSetting: Setting<HudColorMode> = this.fadeSetting
            primaryVisibilityModeSetting.value != HudColorMode.RAW
        })

        this.colorSetting = this.add(primaryColor.forceOpaqueColor().dependsOn(this.colorsSetting))

        val secondaryColor = ColorSetting(
            "SecondColor", Color(
                105,
                86,
                143,
                255
            ), Predicate<Color> { ignored ->
                val secondaryVisibilityModeSetting: Setting<HudColorMode> = this.fadeSetting
                secondaryVisibilityModeSetting.value == HudColorMode.PULSE &&
         this.colorsSetting.isExpanded
            }
        )

        val configuredSecondaryColor = secondaryColor.forceOpaqueColor()
        this.secondColorSetting = this.add(configuredSecondaryColor)
        this.rawColorSettings = ArrayList(this.colorAmountSetting.maximumValue ?: 10)

        this.effectIconOffset = InterpolatedFloat(
            2.0f
        )

        this.clickGuiSearchOffset = InterpolatedFloat(
            2.0f
        )

        this.paddedChatInputHeight = InterpolatedFloat(
            1.5f
        )

        this.chatInputHeight = InterpolatedFloat(
            1.5f
        )
        this.chatInputOverlay = ChatInputOverlay(this)
        val fadeOffsetVisibilitySetting: Setting<Int> = this.fadeOffsetSetting
        val fadeOffsetVisibility = Predicate<Int> { ignored ->
            val offsetVisibilityModeSetting: Setting<HudColorMode> = this.fadeSetting
            if (offsetVisibilityModeSetting.value == HudColorMode.NONE) {
                val rainbowColor = this.colorSetting as ColorSetting
                if (!rainbowColor.isRainbow) {
                    return@Predicate false
                }
            }

            true
        }
        fadeOffsetVisibilitySetting.addVisibilityPredicate(fadeOffsetVisibility)
        val callbackSetting: Setting<Boolean> = this.hudEditorSetting
        val editorCallback = Runnable {
            val editorCallbackCallbackEditorSetting: Setting<Boolean> = this.hudEditorSetting
            val editorCallbackEnabled = editorCallbackCallbackEditorSetting.value
            if (!editorCallbackEnabled) {
                this.hudEditorSetting.value = true
                if (!this.isWorldUnavailable()) {
                    val editorCallbackCapturedMinecraft = ClientAccess.collectiveMinecraft

                    val editor = ClientContext.getHudEditor()
                    editorCallbackCapturedMinecraft.gui.setScreen(editor)
                }
            }
        }
        callbackSetting.setChangeCallback(editorCallback)

        val drawn = false
        this.setDrawn(drawn)
        this.setToggled(true)
        val maximum = this.colorAmountSetting.maximumValue ?: 10
        for (colorNumber in 1..maximum) {
            val capturedColorNumber = colorNumber
            val rawColor = ColorSetting(
                "Color" + colorNumber, Color.white, Predicate<Color> { ignored ->
                    val rawColorVisibilityModeSetting: Setting<HudColorMode> = this.fadeSetting
                    rawColorVisibilityModeSetting.value == HudColorMode.RAW &&
         this.colorsSetting.isExpanded &&
         capturedColorNumber <= this.colorAmountSetting.value
                }
            )

            val configuredRawColor = rawColor.forceOpaqueColor()
            val registeredColor = this.add(configuredRawColor)
            this.rawColorSettings.add(registeredColor)
        }

        val eventBus = ClientAccess.eventBus
        eventBus.subscribe(this.chatInputOverlay)
    }

    enum class ModuleSortMode(private val displayName: String) : NamedEntry {
        ALPHABET("Alphabet"),
        LENGTH("Length");

        override fun getName(): String {
            return this.displayName
        }
    }

    enum class EffectIconMode(private val displayName: String) : NamedEntry {
        KEEP("Keep"),
        MOVE("Move"),
        HIDE("Hide");

        override fun getName(): String {
            return this.displayName
        }
    }

    companion object {
        private var noRenderModule: NoRenderModule? = null

        @JvmStatic
        fun bindReferencedModules(manager: ModuleRegistry) {
            noRenderModule = manager.getModule(NoRenderModule::class.java) as NoRenderModule
            HudColorMode.RAINBOW.bindColorsModule(manager)
        }
    }
}
