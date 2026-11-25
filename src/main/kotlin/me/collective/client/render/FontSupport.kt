package me.collective.client.render

import me.collective.client.ClientAccess
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.util.concurrent.CopyOnWriteArrayList

object FontSupport {
    private val chineseFonts: MutableList<String> = CopyOnWriteArrayList()
    private val japaneseFonts: MutableList<String> = CopyOnWriteArrayList()
    private val bothFonts: MutableList<String> = CopyOnWriteArrayList()

    @JvmStatic
    fun initialize() {
        val chinese = HashSet<Char>()
        val japanese = HashSet<Char>()
        FontLanguage.CHINESE.getRange().forEachCharacter { chinese.add(it) }
        FontLanguage.JAPANESE.getRange().forEachCharacter { japanese.add(it) }
        ClientAccess.executorService.submit {
            val families = HashSet<String>()
            for (font: Font in GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts) {
                val family = font.family
                if (families.add(family)) {
                    val supportsChinese = chinese.all { font.canDisplay(it) }
                    val supportsJapanese = japanese.all { font.canDisplay(it) }
                    if (supportsChinese) chineseFonts.add(family)
                    if (supportsJapanese) japaneseFonts.add(family)
                    if (supportsChinese && supportsJapanese) bothFonts.add(family)
                }
            }
        }
    }

    @JvmStatic
    fun getCompatibleFonts(chinese: Boolean, japanese: Boolean): List<String> {
        return if (chinese) (if (japanese) bothFonts else chineseFonts) else japaneseFonts
    }

    @JvmStatic
    fun supports(font: String, chinese: Boolean, japanese: Boolean): Boolean {
        return getCompatibleFonts(chinese, japanese).any { it.equals(font, ignoreCase = true) }
    }
}
