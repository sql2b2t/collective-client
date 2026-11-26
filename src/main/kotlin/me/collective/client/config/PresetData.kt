package me.collective.client.config

import me.collective.client.ClientAccess
import me.collective.client.settings.NamedEntry
import me.collective.client.utils.FileUtils

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Path

data class PresetData(
    @get:JvmName("presetName") val name: String,
    @get:JvmName("contents") val contents: JsonObject
) : ClientAccess, NamedEntry {

    override fun getName(): String = this.name

    @Throws(IOException::class)
    fun saveToDirectory(directory: Path) {
        val presetName = this.name
        val targetPath = directory.resolve("$presetName.json")
        val gson: Gson = ClientAccess.gson
        val contents: JsonObject = this.contents
        val serializedContents: String = gson.toJson(contents)
        FileUtils.writeUtf8(targetPath, serializedContents)
    }
}
