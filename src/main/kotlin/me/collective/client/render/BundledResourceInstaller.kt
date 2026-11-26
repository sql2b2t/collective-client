package me.collective.client.render

import java.io.IOException
import me.collective.client.CollectiveClient
import me.collective.client.managers.ConfigurationManager
import me.collective.client.utils.FileUtils

class BundledResourceInstaller {
    private val resourcePaths: Map<String, String> = mapOf(
        "textures/overlay.png" to "assets/collective/textures/overlay.png",
        "textures/skin_protect.png" to "assets/collective/textures/skin_protect.png",
        "textures/shine.png" to "assets/collective/textures/shine.png",
        "shaders/sky.frag" to "assets/collective/shaders/sky/sky.frag",
        "shaders/custom.frag" to "assets/collective/shaders/custom/example.frag",
    )

    fun installMissingResources() {
        resourcePaths.forEach(::installMissingResource)
    }

    private fun installMissingResource(relativeDestination: String, bundledResource: String) {
        val destinationPath = ConfigurationManager.ROOT_DIRECTORY.resolve(relativeDestination)
        val destinationFile = destinationPath.toFile()
        if (!destinationFile.exists()) {
            try {
                CollectiveClient::class.java.getResourceAsStream("/$bundledResource")?.use { input ->
                    val resourceBytes = input.readAllBytes()
                    FileUtils.writeBytes(destinationPath, resourceBytes)
                }
            } catch (ioFailure: IOException) {
                ioFailure.printStackTrace()
            }
        }
    }
}
