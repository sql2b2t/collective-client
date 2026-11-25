package me.collective.client.utils

import java.awt.Desktop
import java.net.URI
import java.nio.file.Path
import java.util.Locale

// Кросс-платформенное открытие папок и ссылок. Desktop API доступен не везде
// (Linux без desktop-окружения/Wayland, headless), поэтому есть fallback
// на внешний opener ОС: explorer/open/xdg-open.
object DesktopUtils {

    @JvmStatic
    fun openPath(path: Path) {
        val target = path.toAbsolutePath()
        try {
            Desktop.getDesktop().open(target.toFile())
        } catch (_: Throwable) {
            ProcessBuilder(openerCommand(target.toString())).start()
        }
    }

    @JvmStatic
    fun browse(uri: URI) {
        try {
            Desktop.getDesktop().browse(uri)
        } catch (_: Throwable) {
            ProcessBuilder(browseCommand(uri.toString())).start()
        }
    }

    private fun openerCommand(target: String): List<String> {
        val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        return when {
            os.startsWith("windows") -> listOf("explorer.exe", target)
            os.contains("mac") || os.contains("darwin") -> listOf("open", target)
            else -> listOf("xdg-open", target)
        }
    }

    private fun browseCommand(target: String): List<String> {
        val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        return when {
            os.startsWith("windows") -> listOf("rundll32", "url.dll,FileProtocolHandler", target)
            os.contains("mac") || os.contains("darwin") -> listOf("open", target)
            else -> listOf("xdg-open", target)
        }
    }
}