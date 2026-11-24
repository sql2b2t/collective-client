package me.collective.client.render;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public enum LoadingOverlayTheme {
   RU("ru", Color.white, Color.blue, Color.red),
   UA("ua", new Color(0, 102, 204), new Color(239, 208, 12)),
   BY("by", Color.red, Color.red, new Color(73, 165, 86)),
   LT("lt", new Color(211, 171, 11), new Color(38, 117, 47), Color.red.darker()),
   GOOD_DAY("good_day", new Color(30, 30, 30), new Color(214, 102, 17),
      new Color(30, 30, 30), new Color(214, 102, 17), new Color(30, 30, 30)),
   HALLOWEEN("halloween") {
      @Override
      public List getColors() {
         Color animatedColor = ColorUtils.pulse(
            new Color(255, 121, 50), new Color(147, 61, 50), 3000.0, 0.0
         );
         return Collections.singletonList(animatedColor);
      }
   },
   DEFAULT("") {
      @Override
      public List getColors() {
         Color animatedColor = ColorUtils.pulse(
            new Color(199, 146, 234), new Color(255, 49, 93), 1500.0, 0.0
         );
         return Collections.singletonList(animatedColor);
      }
   };

   private final String countryCode;
   private final List colors = new ArrayList();

   public static LoadingOverlayTheme fromCountryCode(String requestedCode) {
      for (LoadingOverlayTheme theme : values()) {
         if (theme.countryCode.equalsIgnoreCase(requestedCode)) {
            return theme;
         }
      }
      return DEFAULT;
   }

   public List getColors() {
      return this.colors;
   }

   LoadingOverlayTheme(String countryCode, Color... colors) {
      this.countryCode = countryCode;
      this.colors.addAll(List.of(colors));
   }
}
