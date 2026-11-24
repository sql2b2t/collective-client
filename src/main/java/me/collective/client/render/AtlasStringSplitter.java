package me.collective.client.render;

import net.minecraft.client.StringSplitter;

public class AtlasStringSplitter extends StringSplitter {

   public AtlasStringSplitter(AtlasTextRenderer renderer) {
      super(
         (codePoint, ignoredStyle) -> renderer.getCharacterWidth(
            Character.toString(codePoint).charAt(0)
         )
      );
   }

}
