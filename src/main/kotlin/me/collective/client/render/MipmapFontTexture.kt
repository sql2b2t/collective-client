package me.collective.client.render

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import java.util.function.Supplier
import net.minecraft.client.renderer.texture.AbstractTexture

class MipmapFontTexture(
    label: Supplier<String>,
    mipImages: Array<NativeImage>,
) : AbstractTexture() {
    private var mipImages: Array<NativeImage>? = mipImages
    private val mipLevelCount: Int = mipImages.size

    init {
        createTexture(label)
        upload()
    }

    private fun createTexture(label: Supplier<String>) {
        val images = mipImages ?: return
        val device = RenderSystem.getDevice()
        val baseImage = images[0]
        val createdTexture = device.createTexture(
            label,
            5,
            GpuFormat.RGBA8_UNORM,
            baseImage.width,
            baseImage.height,
            1,
            mipLevelCount,
        )
        this.texture = createdTexture
        this.sampler = RenderSystem.getSamplerCache().getSampler(
            AddressMode.CLAMP_TO_EDGE,
            AddressMode.CLAMP_TO_EDGE,
            FilterMode.LINEAR,
            FilterMode.LINEAR,
            true,
        )
        this.textureView = device.createTextureView(createdTexture, 0, mipLevelCount)
    }

    fun upload() {
        val images = mipImages ?: return
        val currentTexture = texture ?: return
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        for (level in images.indices) {
            val image = images[level]
            encoder.writeToTexture(currentTexture, image, level, 0, 0, 0)
        }
    }

    fun getPixels(): NativeImage? {
        return mipImages?.get(0)
    }

    fun setPixels(image: NativeImage) {
        mipImages?.forEach { it.close() }
        mipImages = arrayOf(image)
    }

    override fun close() {
        mipImages?.forEach { it.close() }
        mipImages = null
        super.close()
    }
}
