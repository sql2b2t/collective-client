package me.collective.client.render;

import me.collective.client.render.text.CharacterRange;
import me.collective.client.render.text.ContiguousCharacterRange;
import me.collective.client.render.text.DeltaEncodedCharacterRange;
import me.collective.client.render.text.ExplicitCharacterRange;
import me.collective.client.CollectiveClient;

public enum FontLanguage {
   SPECIAL(new ExplicitCharacterRange('∞', '♫', '♪')),
   LATIN(new ContiguousCharacterRange(0, 383)),
   CYRILLIC(new ContiguousCharacterRange(1024, 1327)),
   CHINESE(new DeltaEncodedCharacterRange(19968, CollectiveClient.class.getResourceAsStream("/assets/collective/font/range_cn.bin"))),
   JAPANESE(
      new DeltaEncodedCharacterRange(19968, CollectiveClient.class.getResourceAsStream("/assets/collective/font/range_jp.bin"))
   ),
   JAPANESE_ALPHABET(new ContiguousCharacterRange(12288, 12543));

   private final CharacterRange characterRange;

   FontLanguage(CharacterRange characterRange) {
      this.characterRange = characterRange;
   }

   public CharacterRange getRange() {
      return this.characterRange;
   }

}
