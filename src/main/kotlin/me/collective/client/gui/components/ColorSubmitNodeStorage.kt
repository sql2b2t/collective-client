package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.VertexConsumer
import me.collective.client.managers.ShaderRegistry
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType

class ColorSubmitNodeStorage : MultiBufferSource {
    var colorArgb: Int = 0
        @JvmName("getColorArgbProp") get
        @JvmName("setColorArgbProp") set

    fun setColorArgb(colorArgb: Int) {
        this.colorArgb = colorArgb
    }

    fun getColorArgb(): Int = this.colorArgb

    override fun getBuffer(renderType: RenderType): VertexConsumer {
        val bufferSource = ShaderRegistry.getBufferSource()
        bufferSource.setColorArgb(this.colorArgb)
        return bufferSource.getBuffer(renderType)
    }
}
