package me.collective.client.render

import it.unimi.dsi.fastutil.chars.Char2CharMap
import it.unimi.dsi.fastutil.chars.Char2CharMaps
import it.unimi.dsi.fastutil.chars.Char2CharOpenHashMap
import java.util.Locale

object TextUtils {
   private val CYRILLIC_KEYBOARD_LAYOUT: Char2CharMap

   init {
      val keyboardLayout = Char2CharOpenHashMap()
      val latinKeys = "qwertyuiop[]asdfghjkl;'zxcvbnm,.QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>`~"
      val cyrillicKeys = "йцукенгшщзхъфывапролджэячсмитьбюЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮёЁ"
      keyboardLayout.defaultReturnValue('\u0000')
      for (index in latinKeys.indices) {
         keyboardLayout.put(cyrillicKeys[index], latinKeys[index])
      }
      CYRILLIC_KEYBOARD_LAYOUT = Char2CharMaps.unmodifiable(keyboardLayout)
   }

   @JvmStatic
   fun normalizeKeyboardLayout(text: String): String {
      val normalized = StringBuilder()
      for (index in text.indices) {
         val character = text[index]
         val replacement = CYRILLIC_KEYBOARD_LAYOUT.get(character)
         normalized.append(if (replacement != '\u0000') replacement else character)
      }
      return normalized.toString()
   }

   @JvmStatic
   fun wrapToWidth(text: String, maximumWidth: Double): List<String> {
      val lines = ArrayList<String>()
      var line = StringBuilder()
      var formattingCode = 65535
      for (index in text.indices) {
         val character = text[index]
         if (character == '\u00A7' && index < text.length - 1) {
            formattingCode = text[index + 1].code
         }

         val fontRenderer = FontRenderer.INSTANCE
         val widthProbe = line.toString() + character.toString()
         if (fontRenderer.getWidth(widthProbe) < maximumWidth) {
            line.append(character)
         } else {
            lines.add(line.toString())
            line = StringBuilder().append(167).append(formattingCode.toChar()).append(character)
         }
      }
      if (line.isNotEmpty()) {
         lines.add(line.toString())
      }
      return lines
   }

   @JvmStatic
   fun splitOnDelimiter(text: String): Array<String> {
      val segments = ArrayList<String>(1)
      var segment = StringBuilder()
      for (index in text.indices) {
         val character = text[index]
         if (character == '\n') {
            segments.add(segment.toString())
            segment = StringBuilder()
         } else {
            segment.append(character)
         }
      }
      segments.add(segment.toString())
      return segments.toTypedArray()
   }

   @JvmStatic
   fun getLastFormattingCode(text: String): Char? {
      var formattingCode = 32
      for (index in text.indices) {
         val character = text[index]
         if (character == '\u00A7' && index + 1 < text.length) {
            formattingCode = text[index + 1].code
         }
      }
      return formattingCode.toChar()
   }

   @JvmStatic
   fun containsIgnoreCase(text: String, query: String): Boolean {
      return text.lowercase(Locale.ROOT).contains(query.lowercase(Locale.ROOT))
   }

   @JvmStatic
   fun splitQuotedArguments(text: String?): Array<String> {
      if (text == null || text.isBlank()) {
         return emptyArray()
      }
      val arguments = ArrayList<String>()
      val argument = StringBuilder()
      var insideQuotes = false
      for (index in text.indices) {
         val character = text[index]
         if (character == '"') {
            insideQuotes = !insideQuotes
         } else if (!Character.isWhitespace(character) || insideQuotes) {
            argument.append(character)
         } else if (argument.isNotEmpty()) {
            arguments.add(argument.toString())
            argument.setLength(0)
         }
      }
      if (argument.isNotEmpty()) {
         arguments.add(argument.toString())
      }
      return arguments.toTypedArray()
   }
}
