package me.collective.client.render

import java.util.function.Consumer
import me.collective.client.ClientContext
import me.collective.client.modules.client.FontsModule
import me.collective.mixin.GuiGraphicsAccessor
import net.minecraft.client.gui.ActiveTextCollector
import net.minecraft.client.gui.ActiveTextCollector.Parameters
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.GuiGraphicsExtractor.HoveredTextEffects
import net.minecraft.client.gui.TextAlignment
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence
import org.joml.Matrix3x2f
import org.joml.Vector2f

open class FontActiveTextCollector(
    private var parameters: Parameters,
    graphics: GuiGraphicsExtractor,
    private val hoveredTextEffects: HoveredTextEffects,
    private val styleConsumer: Consumer<Style>?,
) : Consumer<Style>, ActiveTextCollector {
    private val graphicsAccessor: GuiGraphicsAccessor = graphics as GuiGraphicsAccessor

    override fun accept(
        alignment: TextAlignment,
        x: Int,
        y: Int,
        parameters: Parameters,
        text: FormattedCharSequence,
    ) {
        val checkHoveredStyle = hoveredTextEffects.allowCursorChanges
            || hoveredTextEffects.allowTooltip
            || styleConsumer != null
        val left = alignedX(alignment, x.toFloat(), text)
        val width = FontRenderer.INSTANCE.getWidth(text)
        val height = FontRenderer.INSTANCE.getHeight().toFloat()
        if (checkHoveredStyle) {
            val mouseX = graphicsAccessor.`collective$mouseX`().toFloat()
            val mouseY = graphicsAccessor.`collective$mouseY`().toFloat()
            visitHoveredStyle(
                text, mouseX, mouseY, left, y.toFloat(), width, height, parameters, this
            )
        }

        val fontsModule = fontsModule()
        if (fontsModule != null && ARGB.as8BitChannel(parameters.opacity()) != 0) {
            val color = ARGB.color(parameters.opacity(), -1)
            fontsModule.enqueueText(
                parameters.pose(), text, left, y.toFloat(), color, true
            )
        }
    }

    override fun defaultParameters(parameters: Parameters) {
        this.parameters = parameters
    }

    override fun defaultParameters(): Parameters = parameters

    override fun acceptScrolling(
        text: Component,
        centerX: Int,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        parameters: Parameters,
    ) {
        val width = FontRenderer.INSTANCE.getWidth(text.visualOrderText).toInt()
        val height = FontRenderer.INSTANCE.getHeight()
        defaultScrollingHelper(text, centerX, left, right, top, bottom, width, height, parameters)
    }

    override fun accept(style: Style) {
        if (hoveredTextEffects.allowTooltip && style.hoverEvent != null) {
            graphicsAccessor.`collective$hoverStyle`(style)
        }

        if (hoveredTextEffects.allowCursorChanges && style.clickEvent != null) {
            graphicsAccessor.`collective$clickStyle`(style)
        }

        styleConsumer?.accept(style)
    }

    open class ClickableStyleFinder(
        mouseX: Double,
        mouseY: Double,
        private val includeInsertions: Boolean,
    ) : ActiveTextCollector {
        private val mouseX: Float = mouseX.toFloat()
        private val mouseY: Float = mouseY.toFloat()
        private var parameters: Parameters = DEFAULT_PARAMETERS
        private var result: Style? = null
        private val clickableStyleConsumer: Consumer<Style> = Consumer { style ->
            if (style.clickEvent != null || (includeInsertions && style.insertion != null)) {
                result = style
            }
        }

        override fun defaultParameters(): Parameters = parameters

        override fun defaultParameters(parameters: Parameters) {
            this.parameters = parameters
        }

        override fun accept(
            alignment: TextAlignment,
            x: Int,
            y: Int,
            parameters: Parameters,
            text: FormattedCharSequence,
        ) {
            val left = alignedX(alignment, x.toFloat(), text)
            visitHoveredStyle(
                text,
                mouseX,
                mouseY,
                left,
                y.toFloat(),
                FontRenderer.INSTANCE.getWidth(text),
                FontRenderer.INSTANCE.getHeight().toFloat(),
                parameters,
                clickableStyleConsumer,
            )
        }

        override fun acceptScrolling(
            text: Component,
            centerX: Int,
            left: Int,
            right: Int,
            top: Int,
            bottom: Int,
            parameters: Parameters,
        ) {
            val width = FontRenderer.INSTANCE.getWidth(text).toInt()
            val height = FontRenderer.INSTANCE.getHeight()
            defaultScrollingHelper(text, centerX, left, right, top, bottom, width, height, parameters)
        }

        fun result(): Style? = result

        companion object {
            private val DEFAULT_PARAMETERS = Parameters(Matrix3x2f())
        }
    }

    companion object {
        private var fontsModule: FontsModule? = null

        @JvmField
        var customChatTextEnabled: Boolean = false

        private fun fontsModule(): FontsModule? {
            if (fontsModule == null) {
                fontsModule = ClientContext.getModule(FontsModule::class.java)
            }
            return fontsModule
        }

        internal fun visitHoveredStyle(
            text: FormattedCharSequence,
            mouseX: Float,
            mouseY: Float,
            left: Float,
            top: Float,
            width: Float,
            height: Float,
            parameters: Parameters,
            styleConsumer: Consumer<Style>,
        ) {
            val scissor = parameters.scissor()
            if (scissor != null && (mouseX < scissor.left() || mouseX >= scissor.right() || mouseY < scissor.top() || mouseY >= scissor.bottom())) {
                return
            }
            val localMouse = parameters.pose().invert(Matrix3x2f()).transformPosition(Vector2f(mouseX, mouseY))
            if (!containsPoint(localMouse.x(), localMouse.y(), left, top, left + width, top + height)) {
                return
            }
            val relativeX = localMouse.x() - left
            val relativeY = localMouse.y() - top
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

        private fun alignedX(alignment: TextAlignment, x: Float, text: FormattedCharSequence): Float {
            val width = FontRenderer.INSTANCE.getWidth(text)
            return when (alignment) {
                TextAlignment.LEFT -> x
                TextAlignment.CENTER -> x - width / 2.0f
                TextAlignment.RIGHT -> x - width
            }
        }

        private fun containsPoint(x: Float, y: Float, left: Float, top: Float, right: Float, bottom: Float): Boolean {
            return x >= left && x < right && y >= top && y < bottom
        }
    }
}
