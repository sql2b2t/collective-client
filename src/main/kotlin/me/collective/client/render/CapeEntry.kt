package me.collective.client.render

import net.minecraft.resources.Identifier

@JvmRecord
data class CapeEntry(
    val playerName: String,
    val textureFileName: String
) {
    fun getTexture(): Identifier {
        val texturePath = "capes/${this.textureFileName}"
        return Identifier.fromNamespaceAndPath("collective", texturePath)
    }
}
