package me.collective.client.render

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.platform.TextureUtil
import java.util.function.Supplier
import net.minecraft.client.renderer.texture.AbstractTexture

class MipmapFontTexture(
    @Suppress("UNUSED_PARAMETER") label: Supplier<String>,
    mipImages: Array<NativeImage>,
) : AbstractTexture() {
    private var mipImages: Array<NativeImage>? = mipImages
    private val mipLevelCount: Int = mipImages.size

    init {
        createTexture()
        upload()
    }

    private fun createTexture() {
        val images = mipImages ?: return
        val baseImage = images[0]
        TextureUtil.prepareImage(
            NativeImage.InternalGlFormat.RGBA,
            getId(),
            (mipLevelCount - 1).coerceAtLeast(0),
            baseImage.width,
            baseImage.height,
        )
        setClamp(true)
        setFilter(true, mipLevelCount > 1)
    }

    fun upload() {
        val images = mipImages ?: return
        bind()
        for (level in images.indices) {
            val image = images[level]
            image.upload(level, 0, 0, 0, 0, image.width, image.height, false)
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
        releaseId()
        super.close()
    }
}
