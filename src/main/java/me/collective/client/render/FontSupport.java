package me.collective.client.render;

import me.collective.client.ClientAccess;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FontSupport {
   private static final List<String> chineseFonts = new CopyOnWriteArrayList<>();
   private static final List<String> japaneseFonts = new CopyOnWriteArrayList<>();
   private static final List<String> bothFonts = new CopyOnWriteArrayList<>();

   private FontSupport() {
   }

   public static void initialize() {
      Set<Character> chinese = new HashSet<>();
      Set<Character> japanese = new HashSet<>();
      FontLanguage.CHINESE.getRange().forEachCharacter(chinese::add);
      FontLanguage.JAPANESE.getRange().forEachCharacter(japanese::add);
      ClientAccess.executorService.submit(() -> {
         Set<String> families = new HashSet<>();
         for (Font font : GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
            String family = font.getFamily();
            if (families.add(family)) {
               boolean supportsChinese = chinese.stream().allMatch(font::canDisplay);
               boolean supportsJapanese = japanese.stream().allMatch(font::canDisplay);
               if (supportsChinese) chineseFonts.add(family);
               if (supportsJapanese) japaneseFonts.add(family);
               if (supportsChinese && supportsJapanese) bothFonts.add(family);
            }
         }
      });
   }

   public static List<String> getCompatibleFonts(boolean chinese, boolean japanese) {
      return chinese ? (japanese ? bothFonts : chineseFonts) : japaneseFonts;
   }

   public static boolean supports(String font, boolean chinese, boolean japanese) {
      return getCompatibleFonts(chinese, japanese).stream().anyMatch(font::equalsIgnoreCase);
   }
}
