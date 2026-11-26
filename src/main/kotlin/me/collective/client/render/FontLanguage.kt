package me.collective.client.render

import me.collective.client.CollectiveClient
import me.collective.client.render.text.CharacterRange
import me.collective.client.render.text.ContiguousCharacterRange
import me.collective.client.render.text.DeltaEncodedCharacterRange
import me.collective.client.render.text.ExplicitCharacterRange

enum class FontLanguage(private val characterRange: CharacterRange) {
    SPECIAL(ExplicitCharacterRange('∞', '♫', '♪')),
    LATIN(ContiguousCharacterRange(0, 383)),
    CYRILLIC(ContiguousCharacterRange(1024, 1327)),
    CHINESE(
        DeltaEncodedCharacterRange(
            19968,
            CollectiveClient::class.java.getResourceAsStream("/assets/collective/font/range_cn.bin"),
        )
    ),
    JAPANESE(
        DeltaEncodedCharacterRange(
            19968,
            CollectiveClient::class.java.getResourceAsStream("/assets/collective/font/range_jp.bin"),
        )
    ),
    JAPANESE_ALPHABET(ContiguousCharacterRange(12288, 12543));

    fun getRange(): CharacterRange = characterRange
}
