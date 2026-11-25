package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.ConfigurationManager
import me.collective.client.utils.chat.ChatUtils
import me.collective.mixin.TextureManagerAccessor
import net.minecraft.ChatFormatting
import net.minecraft.client.renderer.texture.SimpleTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

class ReloadCommand : Command("reload") {
    private var reloadFuture: CompletableFuture<Void> = CompletableFuture.completedFuture(null)

    init {
        this.setAliases("rl")
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        root.executes {
            if (!this.reloadFuture.isDone) {
                return@executes 0
            }

            val reloadingMessage = Component.literal("Reloading Collective resources...")
            ChatUtils.printChat(reloadingMessage, ChatUtils.objectSignature(this))

            val future = CompletableFuture.runAsync(
                { reloadResources() },
                ClientAccess.executorService
            )
            this.reloadFuture = future

            future.whenComplete { _, _ ->
                val completionMessage = Component.literal("Reload complete!").withStyle { style ->
                    style.applyFormat(ChatFormatting.GREEN)
                }
                ChatUtils.printChat(completionMessage, ChatUtils.objectSignature(this))
            }
            1
        }
    }

    private fun reloadResources() {
        ClientAccess.collectiveMinecraft.execute {
            ClientContext.shaderRegistry.reloadAll()
        }
        ClientContext.configurationManager.prepareDirectories()
        ClientContext.soundManager.reload()

        val mountedTextures = HashSet<ResourceLocation>()
        val textureManager = ClientAccess.collectiveMinecraft.textureManager
        val allTextures = (textureManager as TextureManagerAccessor).allTextures
        for (key in allTextures.keys) {
            val texture = key as? ResourceLocation ?: continue
            if (texture.namespace == "collective-mount") {
                mountedTextures.add(texture)
            }
        }

        for (mountedTexture in mountedTextures) {
            val dataDirectory = ConfigurationManager.ROOT_DIRECTORY
            val imagePath = dataDirectory.resolve(mountedTexture.path)
            try {
                validatePngSignature(imagePath)
            } catch (invalidImage: Exception) {
                val errorDetail = invalidImage.message
                val errorText = "Invalid Image! $errorDetail"
                val errorMessage = Component.literal(errorText).withStyle { style ->
                    style.applyFormat(ChatFormatting.RED)
                }
                ChatUtils.printChat(errorMessage, ChatUtils.createSignature(-999))
                continue
            }

            ClientAccess.collectiveMinecraft.executeIfPossible {
                val tm = ClientAccess.collectiveMinecraft.textureManager
                tm.release(mountedTexture)
                val replacementTexture = SimpleTexture(mountedTexture)
                tm.registerAndLoad(mountedTexture, replacementTexture)
            }
        }
    }

    companion object {
        private val PNG_SIGNATURE = byteArrayOf(
            (-119).toByte(),
            80.toByte(),
            78.toByte(),
            71.toByte(),
            13.toByte(),
            10.toByte(),
            26.toByte(),
            10.toByte()
        )

        @Throws(IOException::class)
        private fun validatePngSignature(imagePath: Path) {
            Files.newInputStream(imagePath).use { input ->
                val actualSignature = input.readNBytes(PNG_SIGNATURE.size)
                if (!actualSignature.contentEquals(PNG_SIGNATURE)) {
                    throw RuntimeException("File is not a png")
                }
            }
        }
    }
}
