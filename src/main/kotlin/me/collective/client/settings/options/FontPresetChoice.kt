package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

/**
 * Пресеты шрифтов для модуля Fonts. Выбор пресета подставляет имя семейства
 * в настройку Font; если шрифта нет в системе (например, Segoe UI на Linux),
 * validateFont вернёт ближайший поддерживаемый.
 */
enum class FontPresetChoice(
    private val displayName: String,
    /** Имя семейства для настройки Font; null — «Custom» (не трогать ручной ввод). */
    val fontFamily: String?
) : NamedEntry {
    CUSTOM("Custom", null),
    ARIAL("Arial", "Arial"),
    SEGOE_UI("Segoe UI", "Segoe UI"),
    TAHOMA("Tahoma", "Tahoma"),
    VERDANA("Verdana", "Verdana"),
    CALIBRI("Calibri", "Calibri"),
    CONSOLAS("Consolas", "Consolas"),
    CASCADIA_CODE("Cascadia Code", "Cascadia Code"),
    INTER("Inter", "Inter"),
    ROBOTO("Roboto", "Roboto"),
    JETBRAINS_MONO("JetBrains Mono", "JetBrains Mono"),
    FIRA_CODE("Fira Code", "Fira Code"),
    NOTO_SANS("Noto Sans", "Noto Sans"),
    UBUNTU("Ubuntu", "Ubuntu"),
    DEJAVU_SANS("DejaVu Sans", "DejaVu Sans"),
    LIBERATION_SANS("Liberation Sans", "Liberation Sans"),
    SANS_SERIF("SansSerif", "SansSerif"),
    MONOSPACED("Monospace", "Monospace");

    override fun getName(): String = displayName
}
