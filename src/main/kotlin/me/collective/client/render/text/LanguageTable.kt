package me.collective.client.render.text

import me.collective.client.modules.client.UIModule
import net.minecraft.ChatFormatting
import org.apache.commons.io.IOUtils
import java.io.InputStream
import java.nio.charset.StandardCharsets

class LanguageTable {
    private val translations = HashMap<String, String>()

    init {
        load(UIModule.LanguageChoice.ENGLISH.openStream())
    }

    fun load(input: InputStream?) {
        try {
            parseInto(translations, IOUtils.toString(input, StandardCharsets.UTF_8))
        } catch (_: Throwable) {
        }
    }

    fun getTranslation(key: String): String? = translations[key]

    fun getTranslationOrDefault(key: String, fallback: String): String =
        translations.getOrDefault(key, fallback)

    private companion object {
        fun parseInto(table: MutableMap<String, String>, languageText: String) {
            table.clear()
            var sectionPrefix = ""
            for (rawLine in languageText.split("\n")) {
                val line = rawLine.trim()
                if (line.isEmpty() || line[0] == '#' || line[0] == ';') {
                    continue
                }
                if (line[0] == '[' && line[line.length - 1] == ']' && line.length > 2) {
                    sectionPrefix = line.substring(1, line.length - 1) + "."
                    continue
                }

                val separatorIndex = line.indexOf('=')
                if (separatorIndex == -1) {
                    continue
                }
                val trimmedKey = line.substring(0, separatorIndex).trim()
                val key = sectionPrefix + trimmedKey
                var value = line.substring(separatorIndex + 1).trim()
                if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length - 1)
                }
                value = value.replace("\\n", "\n")
                value = value.replace("<red>", ChatFormatting.RED.toString())
                value = value.replace("<yellow>", ChatFormatting.YELLOW.toString())
                table[key] = value
            }
        }
    }
}
