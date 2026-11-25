package me.collective.client.commands

import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.managers.ConfigurationManager
import me.collective.client.settings.options.PresetCategory
import me.collective.client.utils.DesktopUtils
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component
import java.nio.file.Path
import java.util.Locale

class FolderCommand : Command("folder") {

    init {
        setAliases("openfolder")
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        attachDirectory(root, root, ConfigurationManager.ROOT_DIRECTORY)
        attachDirectory(root, literal<Any?>("spammer"), ConfigurationManager.SPAMMER_DIRECTORY)
        attachDirectory(root, literal<Any?>("killsay"), ConfigurationManager.KILLSAY_DIRECTORY)
        attachDirectory(root, literal<Any?>("textures"), ConfigurationManager.TEXTURES_DIRECTORY)
        attachDirectory(root, literal<Any?>("sounds"), ConfigurationManager.SOUNDS_DIRECTORY)
        attachDirectory(root, literal<Any?>("shaders"), ConfigurationManager.SHADERS_DIRECTORY)

        val presetsBuilder = aliasedLiteral("configs", "presets") as RequiredArgumentBuilder<Any?, *>

        for (category in PresetCategory.entries) {
            val literalName = category.name.lowercase(Locale.ROOT)
            val categoryBuilder = literal<Any?>(literalName)
            attachDirectory(presetsBuilder, categoryBuilder, category.directory)
        }

        attachDirectory(root, presetsBuilder, ConfigurationManager.PRESETS_DIRECTORY)
    }

    companion object {
        private fun openDirectory(directory: Path): Int {
            try {
                DesktopUtils.openPath(directory)
            } catch (retryFailure: Throwable) {
                retryFailure.printStackTrace()
                ChatUtils.printChat(
                    Component.literal("Failed to open folder ")
                        .append(ChatUtils.watermarkText(directory.fileName.toString()))
                        .append("."),
                    ChatUtils.DEFAULT_SIGNATURE,
                    ChatUtils.MessagePriority.MID
                )
            }
            return 1
        }

        private fun attachDirectory(
            parent: ArgumentBuilder<Any?, *>,
            child: ArgumentBuilder<Any?, *>,
            directory: Path
        ) {
            child.executes { openDirectory(directory) }
            if (parent != child) {
                parent.then(child)
            }
        }
    }
}
