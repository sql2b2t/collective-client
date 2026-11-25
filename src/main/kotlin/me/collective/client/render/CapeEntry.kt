package me.collective.client.render

import net.minecraft.resources.ResourceLocation

@JvmRecord
data class CapeEntry(
    val playerName: String,
    val textureFileName: String
) {
    fun getTexture(): ResourceLocation {
        val texturePath = "capes/${this.textureFileName}"
        return ResourceLocation.fromNamespaceAndPath("collective", texturePath)
    }
}
