package me.collective.client.render

import java.util.function.Consumer
import me.collective.client.ClientContext
import me.collective.client.modules.client.FontsModule
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence

object FontActiveTextCollector {
    private var fontsModule: FontsModule? = null

    @JvmField
    var customChatTextEnabled: Boolean = false

    private fun fontsModule(): FontsModule? {
        if (fontsModule == null) {
            fontsModule = ClientContext.getModule(FontsModule::class.java)
        }
        return fontsModule
    }

    @JvmStatic
    fun visitHoveredStyle(
        text: FormattedCharSequence,
        mouseX: Float,
        mouseY: Float,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        styleConsumer: Consumer<Style>,
    ) {
        if (!containsPoint(mouseX, mouseY, left, top, left + width, top + height)) {
            return
        }
        val relativeX = mouseX - left
        val relativeY = mouseY - top
        if (relativeY < 0.0f || relativeY >= height) {
            return
        }
        val advance = floatArrayOf(0.0f)
        text.accept { _, style, codePoint ->
            val glyphWidth = FontRenderer.INSTANCE.getWidth(FormattedCharSequence.codepoint(codePoint, Style.EMPTY))
            if (relativeX >= advance[0] && relativeX < advance[0] + glyphWidth) {
                styleConsumer.accept(style)
                false
            } else {
                advance[0] += glyphWidth
                true
            }
        }
    }

    private fun containsPoint(x: Float, y: Float, left: Float, top: Float, right: Float, bottom: Float): Boolean {
        return x >= left && x < right && y >= top && y < bottom
    }
}
