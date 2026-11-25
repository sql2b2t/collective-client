package me.collective.client.render

import java.awt.Color

enum class LoadingOverlayTheme(
    private val countryCode: String,
    vararg colors: Color
) {
    RU("ru", Color.white, Color.blue, Color.red),
    UA("ua", Color(0, 102, 204), Color(239, 208, 12)),
    BY("by", Color.red, Color.red, Color(73, 165, 86)),
    LT("lt", Color(211, 171, 11), Color(38, 117, 47), Color.red.darker()),
    GOOD_DAY(
        "good_day",
        Color(30, 30, 30),
        Color(214, 102, 17),
        Color(30, 30, 30),
        Color(214, 102, 17),
        Color(30, 30, 30)
    ),
    HALLOWEEN("halloween") {
        override fun getColors(): List<Color> {
            val animatedColor = ColorUtils.pulse(
                Color(255, 121, 50), Color(147, 61, 50), 3000.0, 0.0
            )
            return listOf(animatedColor)
        }
    },
    DEFAULT("") {
        override fun getColors(): List<Color> {
            val animatedColor = ColorUtils.pulse(
                Color(199, 146, 234), Color(255, 49, 93), 1500.0, 0.0
            )
            return listOf(animatedColor)
        }
    };

    private val colors: List<Color> = colors.toList()

    open fun getColors(): List<Color> {
        return this.colors
    }

    companion object {
        @JvmStatic
        fun fromCountryCode(requestedCode: String?): LoadingOverlayTheme {
            for (theme in entries) {
                if (theme.countryCode.equals(requestedCode, ignoreCase = true)) {
                    return theme
                }
            }
            return DEFAULT
        }
    }
}
