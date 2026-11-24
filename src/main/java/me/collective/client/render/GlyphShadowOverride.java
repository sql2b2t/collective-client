package me.collective.client.render;

import me.collective.client.ClientContext;
import me.collective.client.modules.render.NoRenderModule;

/**
 * Общее решение для миксинов над {@code GlyphInfo.getShadowOffset}:
 * ленивый доступ к NoRenderModule вместо lookup-а реестра на каждый глиф.
 */
public final class GlyphShadowOverride {
   private static NoRenderModule noRenderModule;

   private GlyphShadowOverride() {
   }

   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }

   public static boolean isOverridden() {
      NoRenderModule module = noRenderModule();
      return module != null
         && module.isToggled()
         && module.textShadowSetting.getValue();
   }
}