package me.collective.client.render.text;

import me.collective.client.utils.inventory.CharacterConsumer;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public class DeltaEncodedCharacterRange implements CharacterRange {
   private final short[] deltas;
   private final int baseCodePoint;

   private static short[] readShorts(byte[] bytes) {
      ShortBuffer shorts = ByteBuffer.wrap(bytes).asShortBuffer();
      short[] values = new short[shorts.remaining()];
      shorts.get(values);
      return values;
   }

   public DeltaEncodedCharacterRange(int baseCodePoint, InputStream input) {
      this.baseCodePoint = baseCodePoint;

      try {
         byte[] bytes = input.readAllBytes();
         this.deltas = readShorts(bytes);
      } catch (Throwable failure) {
         throw new RuntimeException("Couldn't accumulate character range.");
      }
   }

   @Override
   public void forEachCharacter(CharacterConsumer consumer) {
      int codePoint = this.baseCodePoint;

      for (short delta : this.deltas) {
         codePoint += delta;
         consumer.accept((char)codePoint);
      }
   }
}
