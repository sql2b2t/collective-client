package me.collective.client.gui.screens

import com.google.gson.JsonParser
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.config.EnumJsonConverter
import me.collective.client.gui.components.ActionButtonComponent
import me.collective.client.gui.components.ButtonComponent
import me.collective.client.gui.panels.Panel
import me.collective.client.settings.options.PresetCategory
import net.minecraft.client.gui.screens.Screen
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class PresetImportScreen(
    private val returnScreen: Screen,
    private val sourceFile: Path
) : ClientScreen() {

    private val detectedCategory: PresetCategory? = readPresetCategory(sourceFile)
    private val importPanel: Panel = Panel(getCategoryTitle(detectedCategory))

    init {
        this.importPanel.children.add(ButtonComponent(this.importPanel, "Preset: " + getPresetName(sourceFile)))
        if (this.detectedCategory != null) {
            this.importPanel.registerChild(
                ActionButtonComponent(this.importPanel, "Import") {
                    this.importPreset(sourceFile, this.detectedCategory)
                }
            )
        } else {
            for (category in PresetCategory.values()) {
                this.importPanel.registerChild(
                    ActionButtonComponent(this.importPanel, EnumJsonConverter.getName(category)) {
                        this.importPreset(sourceFile, category)
                    }
                )
            }
        }

        this.importPanel.registerChild(
            ActionButtonComponent(this.importPanel, "Cancel") {
                this.closeWithTransition()
            }
        )
        this.panels.add(this.importPanel)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == 257 && this.detectedCategory != null) {
            this.importPreset(this.sourceFile, this.detectedCategory)
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun closeWithTransition() {
        ClientAccess.collectiveMinecraft.setScreen(this.returnScreen)
    }

    private fun getPresetName(sourcePath: Path): String {
        return sourcePath.toFile().name.replace(".json", "")
    }

    private fun importPreset(sourcePath: Path, targetCategory: PresetCategory) {
        val fileName = sourcePath.toFile().name
        try {
            Files.copy(sourcePath, targetCategory.directory.resolve(fileName))
        } catch (copyFailure: IOException) {
            copyFailure.printStackTrace()
        }

        ClientContext.configurationManager.getPresetRegistry(targetCategory).loadPresets()
        ClientAccess.collectiveMinecraft.setScreen(this.returnScreen)
    }

    private fun readPresetCategory(sourcePath: Path): PresetCategory? {
        return try {
            val jsonText = Files.readString(sourcePath)
            val parsedJson = JsonParser.parseString(jsonText)
            if (!parsedJson.isJsonObject) {
                return null
            }
            val jsonObject = parsedJson.asJsonObject
            if (!jsonObject.has("category")) {
                return null
            }
            val categoryName = jsonObject.get("category").asString
            PresetCategory.findByName(categoryName)
        } catch (readFailure: IOException) {
            null
        }
    }

    private fun getCategoryTitle(category: PresetCategory?): String {
        return category?.name ?: "Unknown"
    }

    companion object {
        @JvmStatic
        fun openForDroppedFiles(previousScreen: Screen, droppedPaths: List<Path>): Boolean {
            val jsonPaths = droppedPaths.filter { it.toString().endsWith(".json") }
            if (jsonPaths.size != 1) {
                return false
            }
            ClientAccess.collectiveMinecraft.setScreen(PresetImportScreen(previousScreen, jsonPaths.first()))
            return true
        }
    }
}
