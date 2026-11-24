package me.collective.client.render;

import it.unimi.dsi.fastutil.chars.Char2CharMap;
import it.unimi.dsi.fastutil.chars.Char2CharMaps;
import it.unimi.dsi.fastutil.chars.Char2CharOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

public class TextUtils {
   private static final String WIDTH_PROBE_PATTERN;
   private static final Char2CharMap CYRILLIC_KEYBOARD_LAYOUT;

   public static String normalizeKeyboardLayout(String text) {
      if (text == null) {
         return null;
      }
      StringBuilder normalized = new StringBuilder();
      for (int index = 0; index < text.length(); index++) {
         char character = text.charAt(index);
         char replacement = CYRILLIC_KEYBOARD_LAYOUT.get(character);
         normalized.append(replacement != 0 ? replacement : character);
      }
      return normalized.toString();
   }

   public static List wrapToWidth(String text, double maximumWidth) {
      ArrayList<String> lines = new ArrayList<>();
      StringBuilder line = new StringBuilder();
      int formattingCode = 65535;
      for (int index = 0; index < text.length(); index++) {
         char character = text.charAt(index);
         if (character == 167) {
            if (index < text.length() - 1) {
               formattingCode = text.charAt(index + 1);
            }
         }

         FontRenderer fontRenderer = FontRenderer.INSTANCE;
         String characterText = String.valueOf(character);
         String lineText = line.toString();
         String widthProbe = WIDTH_PROBE_PATTERN
            .replaceFirst("\u0001", Matcher.quoteReplacement(lineText))
            .replaceFirst("\u0001", Matcher.quoteReplacement(characterText));
         if (fontRenderer.getWidth(widthProbe) < maximumWidth) {
            line.append(character);
         } else {
            lines.add(line.toString());
            line = new StringBuilder().append(167).append((char)formattingCode).append(character);
         }
      }
      if (line.length() > 0) {
         lines.add(line.toString());
      }
      return lines;
   }

   public static String[] splitOnDelimiter(String text) {
      ArrayList<String> segments = new ArrayList<>(1);
      StringBuilder segment = new StringBuilder();
      for (int index = 0; index < text.length(); index++) {
         char character = text.charAt(index);
         if (character == 10) {
            segments.add(segment.toString());
            segment = new StringBuilder();
         } else {
            segment.append(character);
         }
      }
      segments.add(segment.toString());
      return segments.toArray(new String[0]);
   }

   public static Character getLastFormattingCode(String text) {
      int formattingCode = 32;
      for (int index = 0; index < text.length(); index++) {
         char character = text.charAt(index);
         if (character == 167) {
            if (index + 1 < text.length()) {
               formattingCode = text.charAt(index + 1);
            }
         }
      }
      return Character.valueOf((char)formattingCode);
   }

   public static boolean containsIgnoreCase(String text, String query) {
      return (text.toLowerCase(Locale.ROOT)).contains(query.toLowerCase(Locale.ROOT));
   }

   public static String[] splitQuotedArguments(String text) {
      if (text == null || text.isBlank()) {
         return new String[0];
      }
      ArrayList<String> arguments = new ArrayList<>();
      StringBuilder argument = new StringBuilder();
      boolean insideQuotes = false;
      for (int index = 0; index < text.length(); index++) {
         char character = text.charAt(index);
         if (character == 34) {
            insideQuotes = !insideQuotes;
         } else if (!Character.isWhitespace(character) || insideQuotes) {
            argument.append(character);
         } else if (!argument.isEmpty()) {
            arguments.add(argument.toString());
            argument.setLength(0);
         }
      }
      if (!argument.isEmpty()) {
         arguments.add(argument.toString());
      }
      return arguments.toArray(String[]::new);
   }

   static {
      WIDTH_PROBE_PATTERN = "\1\1".intern();
      Char2CharOpenHashMap keyboardLayout = new Char2CharOpenHashMap();
      String latinKeys = "qwertyuiop[]asdfghjkl;'zxcvbnm,.QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>`~";
      String cyrillicKeys = "йцукенгшщзхъфывапролджэячсмитьбюЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮёЁ";
      keyboardLayout.defaultReturnValue((char)0);
      for (int index = 0; index < latinKeys.length(); index++) {
         keyboardLayout.put(cyrillicKeys.charAt(index), latinKeys.charAt(index));
      }
      CYRILLIC_KEYBOARD_LAYOUT = Char2CharMaps.unmodifiable(keyboardLayout);
   }
}
