package me.collective.client.gui.screens

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.audio.BuiltinSound
import me.collective.client.gui.components.ButtonComponent
import me.collective.client.gui.panels.Panel
import me.collective.client.managers.ConfigurationManager
import me.collective.client.render.FontRenderer
import me.collective.client.settings.Setting
import me.collective.client.utils.collections.NamedIdentifier
import net.minecraft.client.gui.screens.Screen
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class SoundSelectionScreen(
    @get:JvmName("getSelectedSettingProp") val selectedSetting: Setting<NamedIdentifier>,
    parent: Screen?
) : ClientScreen() {

    init {
        this.parentScreen = parent
        this.rebuildSoundPanels()
    }

    override fun closeWithTransition() {
        val parent = this.parentScreen
        if (parent is ClientScreen) {
            parent.open()
        } else {
            ClientAccess.collectiveMinecraft.gui.setScreen(parent)
        }
    }

    override fun onFilesDrop(paths: List<Path>) {
        for (droppedFile in paths) {
            if (droppedFile.toFile().name.endsWith(".ogg")) {
                val customSoundsDirectory = ConfigurationManager.SOUNDS_DIRECTORY.resolve("custom")
                customSoundsDirectory.toFile().mkdirs()
                try {
                    Files.copy(droppedFile, customSoundsDirectory.resolve(droppedFile.fileName))
                } catch (exception: IOException) {
                    exception.printStackTrace()
                }
            }
        }

        ClientContext.soundManager.reload()
        this.rebuildSoundPanels()
    }

    private fun rebuildSoundPanels() {
        this.panels.clear()
        val soundIds = ClientContext.soundManager.soundIds
        synchronized(soundIds) {
            val panelIndex = AtomicInteger(0)
            soundIds.map { it.category }
                .filter { it.isNotEmpty() }
                .distinct()
                .forEach { category ->
                    val categoryPanel = Panel(FontRenderer.capitalize(category))
                    this.panels.add(categoryPanel)
                    categoryPanel.x += (categoryPanel.width + 3) * panelIndex.getAndIncrement()
                }

            for (soundId in soundIds) {
                for (panel in this.panels) {
                    if (panel.name.equals(soundId.category, ignoreCase = true) &&
                        soundId != BuiltinSound.HORROR.identifier
                    ) {
                        panel.children.add(SoundOptionButton(this, panel, soundId))
                        break
                    }
                }
            }

            for (panel in this.panels) {
                panel.children.sortWith(
                    Comparator.comparing { child ->
                        if (child is ButtonComponent) child.label else ""
                    }
                )
            }
        }
    }

    fun getSelectedSetting(): Setting<NamedIdentifier> = selectedSetting
}
