package me.collective.client.render

import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import java.util.Locale
import java.util.Objects
import kotlin.math.max
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.client.FontsModule
import me.collective.client.modules.player.NameProtectModule
import net.minecraft.client.gui.Font.DisplayMode
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence

class FontRenderer private constructor() : ClientAccess {
    private var _renderer: AtlasTextRenderer? = null

    val renderer: AtlasTextRenderer
        @JvmName("getRendererProp") get() = getRenderer()

    val height: Int
        @JvmName("getHeightProp") get() = getHeight()

    fun createRenderer(): AtlasTextRenderer {
        val fonts = FontsModule.INSTANCE!!
        return AtlasTextRenderer.create(
            fonts.fontSetting.value,
            fonts.sizeSetting.value * 2,
            fonts.styleSetting.value,
            fonts.getLanguages(),
        )
    }

    fun getHeight(): Int {
        return if (FontsModule.INSTANCE!!.isToggled() && _renderer != null) {
            getRenderer().getHeight() - 1
        } else {
            if (ClientAccess.collectiveMinecraft.font == null) {
                return 9
            }
            Objects.requireNonNull(ClientAccess.collectiveMinecraft.font)
            9
        }
    }

    fun flush() {
        getRenderer().flush()
    }

    fun isFontLoading(): Boolean {
        return FontsModule.INSTANCE!!.hasReloadCooldown()
    }

    fun getRenderer(): AtlasTextRenderer {
        if (_renderer == null) {
            _renderer = createRenderer()
        }
        return _renderer!!
    }

    fun getWidth(text: String): Float {
        var resolvedText = text
        val nameProtect = nameProtect()
        if (nameProtect != null && nameProtect.isToggled() && !isWorldUnavailable()) {
            resolvedText = nameProtect.protectNames(resolvedText) ?: resolvedText
        }

        return if (FontsModule.INSTANCE!!.isToggled() && _renderer != null) {
            getRenderer().getWidth(resolvedText).toFloat()
        } else {
            ClientAccess.collectiveMinecraft.font.width(resolvedText).toFloat()
        }
    }

    fun drawWithoutShadow(poses: PoseStack, text: String?, x: Float, y: Float, color: Color) {
        drawWithoutShadow(poses, Component.nullToEmpty(text), x, y, color)
    }

    fun setRenderer(renderer: AtlasTextRenderer?) {
        if (renderer != null) {
            _renderer = renderer
        }
    }

    fun getWidth(sequence: FormattedCharSequence): Float {
        val text = StringBuilder()
        sequence.accept { _, _, codePoint ->
            text.appendCodePoint(codePoint)
            true
        }
        return getWidth(text.toString())
    }

    fun drawWithShadow(poses: PoseStack, text: Component, x: Float, y: Float, argb: Int) {
        drawWithShadow(poses, text, x, y, argb, DisplayMode.NORMAL)
    }

    fun drawWithShadow(poses: PoseStack, text: Component, x: Float, y: Float, argb: Int, displayMode: DisplayMode) {
        if (ARGB.alpha(argb) == 0) {
            return
        }
        if (FontsModule.INSTANCE!!.isToggled()) {
            getRenderer().draw(poses, text.visualOrderText, x, y, argb, true)
        } else {
            drawTextInBatch(
                ClientAccess.collectiveMinecraft.font,
                text,
                x.toInt().toFloat(),
                y.toInt().toFloat(),
                argb,
                true,
                poses.last().pose(),
                getRenderer().getBufferSource(),
                displayMode,
                0,
                15728880,
            )
        }
    }

    fun drawWithoutShadow(poses: PoseStack, text: Component, x: Float, y: Float, color: Color) {
        drawWithoutShadow(poses, text, x, y, color.hashCode(), DisplayMode.NORMAL)
    }

    fun drawWithoutShadow(poses: PoseStack, text: String?, x: Float, y: Float, argb: Int, displayMode: DisplayMode) {
        drawWithoutShadow(poses, Component.nullToEmpty(text), x, y, argb, displayMode)
    }

    fun drawWithoutShadow(
        poses: PoseStack,
        text: Component,
        x: Float,
        y: Float,
        argb: Int,
        displayMode: DisplayMode,
    ) {
        if (ARGB.alpha(argb) == 0) {
            return
        }
        if (FontsModule.INSTANCE!!.isToggled()) {
            getRenderer().draw(poses, text.visualOrderText, x, y, argb, false)
        } else {
            drawTextInBatch(
                ClientAccess.collectiveMinecraft.font,
                text,
                x.toInt().toFloat(),
                y.toInt().toFloat(),
                argb,
                false,
                poses.last().pose(),
                getRenderer().getBufferSource(),
                displayMode,
                0,
                15728880,
            )
        }
    }

    fun getWidth(component: Component): Float {
        return getWidth(component.string)
    }

    fun drawWithShadow(poses: PoseStack, text: Component, x: Float, y: Float, scale: Float, color: Color) {
        poses.pushPose()
        try {
            poses.scale(scale, scale, scale)
            drawWithShadow(poses, text, x / scale, y / scale, color)
        } finally {
            poses.popPose()
        }
    }

    fun drawWithShadow(poses: PoseStack, text: String?, x: Float, y: Float, scale: Float, color: Color) {
        drawWithShadow(poses, Component.nullToEmpty(text), x, y, scale, color)
    }

    fun drawWithShadow(poses: PoseStack, text: Component, x: Float, y: Float, color: Color) {
        drawWithShadow(poses, text, x, y, color.hashCode())
    }

    fun drawWithShadow(poses: PoseStack, text: Component, x: Float, y: Float, color: Color, displayMode: DisplayMode) {
        drawWithShadow(poses, text, x, y, color.hashCode(), displayMode)
    }

    fun drawWithShadow(poses: PoseStack, text: String?, x: Float, y: Float, color: Color, displayMode: DisplayMode) {
        drawWithShadow(poses, Component.nullToEmpty(text), x, y, color.hashCode(), displayMode)
    }

    fun drawWithShadow(poses: PoseStack, text: String?, x: Float, y: Float, color: Color) {
        drawWithShadow(poses, Component.nullToEmpty(text), x, y, color)
    }

    companion object {
        @JvmField
        val INSTANCE: FontRenderer = FontRenderer()

        private var nameProtect: NameProtectModule? = null

        private fun nameProtect(): NameProtectModule? {
            if (nameProtect == null) {
                nameProtect = ClientContext.getModule(NameProtectModule::class.java)
            }
            return nameProtect
        }

        @JvmStatic
        fun getZOffset(): Int = 0

        @JvmStatic
        fun getTranslate(): Int = FontsModule.INSTANCE!!.translateSetting.value

        @JvmStatic
        fun getShift(): Int = FontsModule.INSTANCE!!.shiftSetting.value

        @JvmStatic
        fun getShadowOffset(): Float = FontsModule.INSTANCE!!.shadowSetting.value

        @JvmStatic
        fun capitalize(value: Any): String {
            val text = value.toString()
            return text[0].uppercaseChar() + text.lowercase(Locale.ROOT).substring(1)
        }

        @JvmStatic
        fun sanitizeFileName(name: String): String {
            return name.replace("[^a-zA-Z0-9.\\-]".toRegex(), "_")
        }

        @JvmStatic
        fun containsCharacter(text: String, character: Int): Boolean {
            for (index in text.indices) {
                if (text[index].code == character) {
                    return true
                }
            }
            return false
        }

        @JvmStatic
        fun capitalizeWords(value: Any): String {
            val result = StringBuilder()
            val text = value.toString()

            for (word in text.split(" ".toRegex()).dropLastWhile { it.isEmpty() }) {
                result.append(capitalize(word))
                result.append(" ")
            }

            result.deleteCharAt(max(result.length - 1, 0))
            return result.toString()
        }
    }
}
