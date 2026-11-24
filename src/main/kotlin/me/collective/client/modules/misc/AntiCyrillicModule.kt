package me.collective.client.modules.misc


import me.collective.client.events.ClientPacketListenerSendChatEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.render.FontRenderer
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.Setting

import me.collective.mixin.ServerboundChatCommandPacketAccessor
import java.util.HashMap
import java.util.regex.Matcher
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket

class AntiCyrillicModule : Module("AntiCyrillic", "Replaces cyrillic letters in your messages with latin equivalents.", ModuleCategory.MISC) {
    private val modeSetting: Setting<ModeChoice>

    init {
        modeSetting = add(EnumSetting("Mode", ModeChoice.CHAT))
    }

    override fun getInfo(): String? {
        return try {
            FontRenderer.capitalize(modeSetting.value)
        } catch (ignored: Exception) {
            null
        }
    }

    @EventHandler
    private fun onCommandPacket(event: PacketEvent.Send) {
        if (event.packet is ServerboundChatCommandPacket &&
         modeSetting.value != ModeChoice.CHAT) {
            val packet = event.packet as ServerboundChatCommandPacket
            (packet as ServerboundChatCommandPacketAccessor).setCommand(normalizeMessage(packet.command()))
        }
    }

    @EventHandler
    private fun onChatMessage(event: ClientPacketListenerSendChatEvent) {
        if (modeSetting.value != ModeChoice.COMMANDS) {
            val message = event.message
            event.message = normalizeMessage(message)
        }
    }

    private fun normalizeMessage(message: String): String {
        var normalized = message
        if (!normalized.startsWith("/")) {
            normalized = transliterate(normalized)
        } else {
            val words = normalized.split(" ")
            if (words.size > 1) {
                val command = StringBuilder()
                for (index in words.size - 1 downTo 0) {
                    val word = if (index == 0) words[0] else transliterate(words[index])
                    command.insert(0, word + " ")
                }
                normalized = command.toString()
            }
        }
        return if (normalized.length > MESSAGE_LENGTH_LIMIT) normalized.substring(0, MESSAGE_LENGTH_LIMIT) else normalized
    }

    private fun transliterate(message: String): String {
        var translated = message
        for (index in translated.length - 1 downTo 0) {
            val character = translated[index]
            if (TRANSLITERATIONS.containsKey(character)) {
                val prefix = translated.substring(0, index)
                val replacement = TRANSLITERATIONS[character]!!
                val suffix = translated.substring(index + 1)
                translated = joinTransliterationParts(prefix, replacement, suffix)
            }
        }
        return translated
    }

    private fun joinTransliterationParts(prefix: String, replacement: String, suffix: String): String {
        var recipe = "\u0001\u0001\u0001"
        for (part in arrayOf(prefix, replacement, suffix)) {
            recipe = Regex("\u0001").replaceFirst(recipe, Matcher.quoteReplacement(part))
        }
        return recipe
    }

    enum class ModeChoice(private val displayName: String) : NamedEntry {
        CHAT("Chat"), COMMANDS("Commands"), BOTH("Both");

        override fun getName(): String {
            return displayName
        }
    }

    companion object {
        private const val MESSAGE_LENGTH_LIMIT = 256
        private val TRANSLITERATIONS: Map<Char, String> = createTransliterations()

        private fun createTransliterations(): Map<Char, String> {
            val replacements: MutableMap<Char, String> = HashMap()
            replacements['\u0401'] = "E"
            replacements['\u0410'] = "A"
            replacements['\u0411'] = "6"
            replacements['\u0412'] = "B"
            replacements['\u0413'] = "r"
            replacements['\u0414'] = "D"
            replacements['\u0415'] = "E"
            replacements['\u0416'] = ")I("
            replacements['\u0417'] = "3"
            replacements['\u0418'] = "U"
            replacements['\u0419'] = "u'"
            replacements['\u041a'] = "K"
            replacements['\u041b'] = "JI"
            replacements['\u041c'] = "M"
            replacements['\u041d'] = "H"
            replacements['\u041e'] = "O"
            replacements['\u041f'] = "II"
            replacements['\u0420'] = "P"
            replacements['\u0421'] = "C"
            replacements['\u0422'] = "T"
            replacements['\u0423'] = "y"
            replacements['\u0424'] = "qp"
            replacements['\u0425'] = "X"
            replacements['\u0426'] = "U,"
            replacements['\u0427'] = "4"
            replacements['\u0428'] = "LLI"
            replacements['\u0429'] = "LLI,"
            replacements['\u042a'] = "'b"
            replacements['\u042b'] = "bI"
            replacements['\u042c'] = "b"
            replacements['\u042d'] = "3"
            replacements['\u042e'] = "IO"
            replacements['\u042f'] = "9I"
            replacements['\u0430'] = "a"
            replacements['\u0431'] = "6"
            replacements['\u0432'] = "B"
            replacements['\u0433'] = "r"
            replacements['\u0434'] = "D"
            replacements['\u0435'] = "e"
            replacements['\u0436'] = ")I("
            replacements['\u0437'] = "3"
            replacements['\u0438'] = "u"
            replacements['\u0439'] = "u'"
            replacements['\u043a'] = "K"
            replacements['\u043b'] = "JI"
            replacements['\u043c'] = "M"
            replacements['\u043d'] = "H"
            replacements['\u043e'] = "o"
            replacements['\u043f'] = "n"
            replacements['\u0440'] = "p"
            replacements['\u0441'] = "c"
            replacements['\u0442'] = "T"
            replacements['\u0443'] = "y"
            replacements['\u0444'] = "qp"
            replacements['\u0445'] = "X"
            replacements['\u0446'] = "u,"
            replacements['\u0447'] = "4"
            replacements['\u0448'] = "LLI"
            replacements['\u0449'] = "LLI,"
            replacements['\u044a'] = "'b"
            replacements['\u044b'] = "bI"
            replacements['\u044c'] = "b"
            replacements['\u044d'] = "3"
            replacements['\u044e'] = "IO"
            replacements['\u044f'] = "9I"
            replacements['\u0451'] = "E"
            return replacements
        }
    }
}
