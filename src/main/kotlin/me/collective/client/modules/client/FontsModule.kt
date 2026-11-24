package me.collective.client.modules.client

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.render.FontSupport
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component
import java.util.concurrent.ThreadLocalRandom
import me.collective.client.events.MinecraftTickEvent
import me.collective.client.events.HudRenderEvent
import me.collective.client.events.ClientInitializationEvent
import me.collective.client.events.EventHandler
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.render.GuiDrawUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.AtlasTextRenderer
import me.collective.client.render.FontLanguage
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.StringSetting
import me.collective.client.settings.options.FontPresetChoice
import me.collective.client.settings.options.FontsStyleChoice

import com.mojang.blaze3d.platform.Window
import com.mojang.blaze3d.vertex.PoseStack
import java.util.concurrent.atomic.AtomicBoolean
import net.minecraft.client.Minecraft
import net.minecraft.util.FormattedCharSequence
import org.joml.Matrix3x2fc
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW

class FontsModule : Module("Fonts", "Manages the client's font renderer.", ModuleCategory.CLIENT) {
    @JvmField
    val presetSetting: Setting<FontPresetChoice> = add(EnumSetting("Preset", FontPresetChoice.CUSTOM))

    @JvmField
    val fontSetting: Setting<String> = add(StringSetting("Font", "Arial"))

    @JvmField
    val styleSetting: Setting<FontsStyleChoice> = add(EnumSetting("Style", FontsStyleChoice.PLAIN))

    @JvmField
    val sizeSetting: Setting<Int> = add(NumberSetting("Size", 16, 12, 20))

    @JvmField
    val antiAliasSetting: Setting<Boolean> = add(BooleanSetting("AntiAlias", true))

    @JvmField
    val chatSetting: Setting<Boolean> = add(BooleanSetting("Chat", false))

    private val offsetsSetting: Setting<Boolean>

    @JvmField
    val shiftSetting: Setting<Int>

    @JvmField
    val translateSetting: Setting<Int>

    @JvmField
    val shadowSetting: Setting<Float>

    private val extraCharsSetting: Setting<Boolean>
    private val simplifiedChineseSetting: Setting<Boolean>
    private val japaneseSetting: Setting<Boolean>
    private val transformedPosition: Vector2f = Vector2f()
    private val queuedDraws: MutableList<QueuedDraw> = ArrayList()

    init {
        val offsets = BooleanSetting("Offsets", false)
        offsets.markAsGroup()
        offsets.markVisibilityDependency()
        offsetsSetting = add(offsets)
        shiftSetting = add(NumberSetting<Int>("Shift", -2, -4, 4).dependsOn(offsetsSetting))
        translateSetting = add(NumberSetting<Int>("Translate", -1, -4, 4).dependsOn(offsetsSetting))
        val shadow = NumberSetting<Float>("Shadow", 0.7f, 0.5f, 1.0f)
        shadowSetting = add(shadow.dependsOn(offsetsSetting))
        val extraChars = BooleanSetting("ExtraChars", false)
        extraChars.markAsGroup()
        extraChars.markVisibilityDependency()
        extraCharsSetting = add(extraChars)
        simplifiedChineseSetting = add(BooleanSetting("SimplifiedChinese", false).dependsOn(extraCharsSetting))
        japaneseSetting = add(BooleanSetting("Japanese", false).dependsOn(extraCharsSetting))
        styleSetting.setChangeCallback(RELOAD_RENDERER)
        antiAliasSetting.setChangeCallback(RELOAD_RENDERER)
        japaneseSetting.setChangeCallback { validateFont(true) }
        simplifiedChineseSetting.setChangeCallback { validateFont(true) }
        sizeSetting.setChangeCallback { reloadRequested.set(true) }
        fontSetting.setChangeCallback { validateFont(true) }
        presetSetting.setChangeCallback {
            val presetFont = presetSetting.value.fontFamily
            if (presetFont != null && presetFont != fontSetting.value) {
                fontSetting.value = presetFont
            }
        }
        INSTANCE = this
        ClientAccess.eventBus.subscribeStatic(FontsModule::class.java)
        setDrawn(false)
    }

    fun hasReloadCooldown(): Boolean {
        return reloadCooldownTicks > 0
    }

    fun getLanguages(): Set<FontLanguage> {
        val languages: MutableSet<FontLanguage> = HashSet(
            listOf(FontLanguage.SPECIAL, FontLanguage.LATIN, FontLanguage.CYRILLIC)
        )
        if (japaneseSetting.value) {
            languages.addAll(listOf(FontLanguage.JAPANESE, FontLanguage.JAPANESE_ALPHABET))
        }
        if (simplifiedChineseSetting.value) {
            languages.add(FontLanguage.CHINESE)
        }
        return languages
    }

    override fun onToggle() {
        reloadCooldownTicks = 3
        validateFont(false)
    }

    private fun flushQueuedDraws(poseStack: PoseStack) {
        if (queuedDraws.isNotEmpty()) {
            GuiDrawUtils.flush(true)
            for (draw in queuedDraws) {
                draw.render(poseStack)
            }
            queuedDraws.clear()
            GuiDrawUtils.flush(true)
        }
    }

    fun enqueueText(
        transform: Matrix3x2fc, text: FormattedCharSequence, x: Float, y: Float, color: Int, shadow: Boolean
    ) {
        transformedPosition.set(0.0f)
        transform.transformPosition(x, y, transformedPosition)
        queuedDraws.add(QueuedText(text, transformedPosition.x(), transformedPosition.y(), color, shadow))
    }

    fun enqueueRectangle(
        transform: Matrix3x2fc, left: Float, top: Float, right: Float, bottom: Float, color: Int
    ) {
        transformedPosition.set(0.0f)
        transform.transformPosition(left, top, transformedPosition)
        val transformedLeft = transformedPosition.x()
        val transformedTop = transformedPosition.y()
        transform.transformPosition(right, bottom, transformedPosition)
        queuedDraws.add(QueuedRectangle(transformedLeft, transformedTop, transformedPosition.x(), transformedPosition.y(), color))
    }

    fun validateFont(reload: Boolean) {
        if (UIModule.INSTANCE.languageSetting.value == UIModule.LanguageChoice.CHINESE &&
         !simplifiedChineseSetting.value
        ) {
            simplifiedChineseSetting.value = true
            return
        }
        val chinese = simplifiedChineseSetting.value
        val japanese = japaneseSetting.value
        if (!chinese && !japanese || FontSupport.supports(fontSetting.value, chinese, japanese)) {
            if (reload) RELOAD_RENDERER.run()
            return
        }
        var replacement: String? = null
        val preferred = if (chinese) {
            if (japanese) listOf("SansSerif") else listOf("Microsoft YaHei", "Microsoft JhengHei")
        } else {
            listOf("MS PGothic")
        }
        for (font in preferred) {
            if (FontSupport.supports(font, chinese, japanese)) {
                replacement = font
                break
            }
        }
        if (replacement == null) {
            val compatible = FontSupport.getCompatibleFonts(chinese, japanese)
            if (compatible.isNotEmpty()) {
                replacement = compatible[ThreadLocalRandom.current().nextInt(compatible.size)]
            }
        }
        if (replacement != null) {
            val key = if (chinese) (if (japanese) "Both" else "Chinese") else "Japanese"
            val characters = if (chinese) (if (japanese) "Chinese and Japanese" else "Chinese") else "Japanese"
            val fallback = "Your font has been changed to \"%s\" because your old font does not support $characters characters."
            val message = ClientContext.languageTable.getTranslationOrDefault("fonts.${key}Unsupported", fallback)
            ChatUtils.printChat(Component.literal(String.format(message, replacement)), ChatUtils.objectSignature(this), ChatUtils.MessagePriority.MID)
            fontSetting.value = replacement
        } else if (reload) {
            RELOAD_RENDERER.run()
        }
    }

    @EventHandler
    private fun onHudRender(event: HudRenderEvent.Unconditional) {
        flushQueuedDraws(event.poseStack!!)
    }

    private interface QueuedDraw {
        fun render(poseStack: PoseStack)
    }

    private data class QueuedText(
        val text: FormattedCharSequence,
        val x: Float,
        val y: Float,
        val color: Int,
        val shadow: Boolean
    ) : QueuedDraw {
        override fun render(poseStack: PoseStack) {
            val chatScale = ClientAccess.collectiveMinecraft.options.chatScale().get().toFloat()
            poseStack.pushPose()
            poseStack.scale(chatScale, chatScale, 1.0f)
            val renderer: AtlasTextRenderer = FontRenderer.INSTANCE.renderer
            renderer.draw(poseStack, text, x / chatScale, y / chatScale, color, shadow)
            poseStack.popPose()
        }
    }

    private data class QueuedRectangle(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val color: Int
    ) : QueuedDraw {
        override fun render(poseStack: PoseStack) {
            GuiDrawUtils.fillRect(
                poseStack,
                left,
                top,
                right,
                bottom,
                color
            )
        }
    }

    companion object {
        @JvmField
        var INSTANCE: FontsModule? = null

        private val RELOAD_RENDERER = Runnable {
            if (ClientAccess.collectiveMinecraft.player != null ||
         ClientAccess.collectiveMinecraft.screen is ClientScreen
            ) {
                FontRenderer.INSTANCE.setRenderer(FontRenderer.INSTANCE.createRenderer())
            }
        }
        private val reloadRequested = AtomicBoolean(false)
        private var reloadCooldownTicks: Int = 0

        @JvmStatic
        @EventHandler
        private fun onClientInitialized(event: ClientInitializationEvent) {
            INSTANCE!!.setToggled(true)
            reloadRequested.set(true)
        }

        @JvmStatic
        @EventHandler
        private fun onMinecraftTick(event: MinecraftTickEvent) {
            val minecraft: Minecraft = ClientAccess.collectiveMinecraft
            val window: Window = minecraft.window
            val windowHandle: Long = window.handle()
            if (GLFW.glfwGetMouseButton(windowHandle, 0) != 1) {
                if (reloadRequested.get()) {
                    FontRenderer.INSTANCE.setRenderer(FontRenderer.INSTANCE.createRenderer())
                    reloadRequested.set(false)
                    reloadCooldownTicks = 3
                }
            }
            if (reloadCooldownTicks > 0) {
                reloadCooldownTicks--
            }
        }
    }
}
