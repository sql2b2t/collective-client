package me.collective.client.render.text;

import me.collective.client.utils.inventory.CharacterConsumer;

public class ContiguousCharacterRange implements CharacterRange {
   private final int firstCodePoint;
   private final int lastCodePoint;
   public ContiguousCharacterRange(int firstCodePoint, int lastCodePoint) {
      this.firstCodePoint = firstCodePoint;
      this.lastCodePoint = lastCodePoint;
   }

   @Override
   public void forEachCharacter(CharacterConsumer consumer) {
      for (int codePoint = this.firstCodePoint;
         codePoint <= this.lastCodePoint;
         codePoint++
      ) {
         consumer.accept((char)codePoint);
      }
   }
}
