package me.collective.client.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.function.Predicate
import me.collective.client.ClientContext
import me.collective.client.managers.AbstractRegistry
import me.collective.client.modules.Module

class ModulePresetCodec {
    fun decode(encodedPreset: String): DecodedPreset {
        val normalizedPreset = encodedPreset.trim().replace("\n", "")
        val jsonText = try {
            String(Base64.getDecoder().decode(normalizedPreset), StandardCharsets.UTF_8)
        } catch (decodeFailure: IllegalArgumentException) {
            throw IllegalArgumentException()
        }
        val parsedJson = try {
            JsonParser.parseString(jsonText)
        } catch (parseFailure: RuntimeException) {
            throw IllegalArgumentException()
        }
        if (!parsedJson.isJsonObject) {
            throw IllegalArgumentException()
        }

        val presetJson = parsedJson.getAsJsonObject()
        val moduleConfigName = LegacyBranding.canonicalModuleName(this.readModuleConfigName(presetJson))
        val registry = ClientContext.moduleRegistry
        val matchesModule = Predicate<Module> { candidateModule ->
            val candidateName = candidateModule.getConfigName()
            candidateName == moduleConfigName
        }
        val module = registry.findFirstOrNull(matchesModule)
        if (module == null) {
            throw IllegalArgumentException()
        }
        return DecodedPreset(module, presetJson)
    }

    private fun readModuleConfigName(presetJson: JsonObject): String {
        val moduleElement = when {
            presetJson.has("m") -> presetJson.get("m")
            presetJson.has("module") -> presetJson.get("module")
            else -> throw IllegalArgumentException()
        }
        if (moduleElement == null || !moduleElement.isJsonPrimitive) {
            throw IllegalArgumentException()
        }
        return moduleElement.asString
    }

    fun encode(module: Module): String {
        val moduleJson = module.toJson()
        val presetJson = moduleJson.getAsJsonObject()
        presetJson.remove("key")
        presetJson.remove("state")
        presetJson.remove("mouse")
        presetJson.remove("enabled")
        presetJson.remove("drawn")
        val moduleConfigName = module.getConfigName()
        presetJson.addProperty("m", moduleConfigName)
        val base64Encoder = Base64.getEncoder()
        val jsonText = presetJson.toString()
        val jsonBytes = jsonText.toByteArray(StandardCharsets.UTF_8)
        return base64Encoder.encodeToString(jsonBytes)
    }

    data class DecodedPreset(
        @get:JvmName("module") val module: Module,
        @get:JvmName("json") val json: JsonObject
    )
}
