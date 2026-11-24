package me.collective.scripting

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.events.EventHandler
import me.collective.client.events.HudRenderEvent
import me.collective.client.modules.HudWidgetModule
import me.collective.client.render.FontRenderer
import me.collective.client.render.HudWidgetRenderer
import me.collective.client.settings.options.HudAnchor
import net.minecraft.network.chat.Component
import java.util.function.BiConsumer

class ScriptHudElement(
    name: String,
    description: String,
    defaultX: Float,
    defaultY: Float
) : HudWidgetModule(name) {
    private var scriptContext: ScriptContext? = null
    private var errorCount: Int = 0
    private var onRenderAction: BiConsumer<PoseStack, Float>? = null
    private var defaultWidth: Float = 100f
    private var defaultHeight: Float = 12f

    init {
        setDescription(description)
        setRenderer(ScriptRenderer())
        getRenderer().setAnchor(HudAnchor.NONE)
        getRenderer().setX(defaultX, false)
        getRenderer().setY(defaultY, false)
    }

    /** Привязка к скрипту-владельцу (для отчётов об ошибках). */
    fun setScriptContext(ctx: ScriptContext) {
        this.scriptContext = ctx
    }

    /** Размер элемента в HUD-редакторе. */
    fun setSize(width: Float, height: Float): ScriptHudElement {
        this.defaultWidth = width
        this.defaultHeight = height
        return this
    }

    /** Колбэк отрисовки: (PoseStack, partialTick). Координаты — относительные (0,0 = левый верх элемента). */
    fun onRender(action: BiConsumer<PoseStack, Float>): ScriptHudElement {
        this.onRenderAction = action
        return this
    }

    /** Упрощение: текст с тенью в левом верхнем углу элемента. */
    fun drawText(poses: PoseStack, text: String, x: Float, y: Float, argb: Int) {
        FontRenderer.INSTANCE.drawWithShadow(poses, Component.literal(text), x, y, argb)
    }

    override fun renderContent(poses: PoseStack) {
        if (onRenderAction == null || !isToggled()) return
        try {
            onRenderAction!!.accept(poses, ClientAccessPartial.partialTick())
        } catch (t: Throwable) {
            handleError(t)
        }
    }

    private fun handleError(t: Throwable) {
        errorCount++
        val reported = scriptContext?.reportError(name, t) ?: errorCount

        if ((errorCount >= ScriptContext.MAX_ERRORS || reported >= ScriptContext.MAX_ERRORS) && isToggled()) {
            setToggled(false)
        }
    }

    /** Минирендерер: рамка+содержимое берутся из базового класса, размеры — из скрипта. */
    private inner class ScriptRenderer : HudWidgetRenderer(this) {
        override fun calculateDimensions(): FloatArray {
            return floatArrayOf(defaultWidth, defaultHeight)
        }

        override fun render(poses: PoseStack, mouseX: Double, mouseY: Double) {
            super.render(poses, mouseX, mouseY)
            poses.pushPose()
            poses.translate(getContentX(defaultWidth), getContentY(defaultHeight), 0f)
            renderContent(poses)
            poses.popPose()
        }
    }

    /** Доступ к partialTick последнего кадра HUD (для совместимости сигнатуры колбэка). */
    private class ClientAccessPartial {
        companion object {
            @Volatile
            var partialTick: Float = 1f

            fun partialTick(): Float = partialTick
        }
    }

    @EventHandler
    private fun onHudRender(event: HudRenderEvent) {
        ClientAccessPartial.partialTick = event.partialTick
    }
}
