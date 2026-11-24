package me.collective.client.render.text;

import me.collective.client.modules.client.UIModule;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import net.minecraft.ChatFormatting;
import org.apache.commons.io.IOUtils;

public final class LanguageTable {
   private final Map<String, String> translations = new HashMap<>();

   public LanguageTable() {
      InputStream languageInput = UIModule.LanguageChoice.ENGLISH.openStream();
      this.load(languageInput);
   }

   public void load(InputStream input) {
      try {
         Map<String, String> targetTranslations = this.translations;
         Charset encoding = StandardCharsets.UTF_8;
         parseInto(targetTranslations, IOUtils.toString(input, encoding));
      } catch (Throwable ignoredFailure) {
      }
   }

   private static void parseInto(Map<String, String> table, String languageText) {
      table.clear();
      String sectionPrefix = "";
      for (String line : languageText.split("\n")) {
         line = line.trim();
         if (line.isEmpty()) {
            continue;
         }
         if (line.charAt(0) == '#') {
            continue;
         }
         if (line.charAt(0) == ';') {
            continue;
         }
         if (line.charAt(0) == '['
            && line.charAt(line.length() - 1) == ']'
            && line.length() > 2) {
            int sectionNameEnd = line.length() - 1;
            sectionPrefix = line.substring(1, sectionNameEnd) + ".";
            continue;
         }

         int separatorIndex = line.indexOf('=');
         if (separatorIndex == -1) {
            continue;
         }
         String untrimmedKey = line.substring(0, separatorIndex);
         String trimmedKey = untrimmedKey.trim();
         String key = (sectionPrefix + "\u0001")
            .replaceFirst("\u0001", Matcher.quoteReplacement(trimmedKey));
         String untrimmedValue = line.substring(separatorIndex + 1);
         String value = untrimmedValue.trim();
         if (value.length() >= 2
            && value.startsWith("\"")
            && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
         }
         value = value.replace("\\n", "\n");
         value = value.replace("<red>", ChatFormatting.RED.toString());
         value = value.replace("<yellow>", ChatFormatting.YELLOW.toString());
         table.put(key, value);
      }
   }

   public String getTranslation(String key) {
      return this.translations.get(key);
   }

   public String getTranslationOrDefault(String key, String fallback) {
      return this.translations.getOrDefault(key, fallback);
   }
}
