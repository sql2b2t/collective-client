package me.collective.client.managers;

import me.collective.client.ClientContext;
import me.collective.client.settings.JsonConfigurable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ConfigurableRegistry implements Iterable<JsonConfigurable> {
   private final List<JsonConfigurable> configurables = new ArrayList<>();

   ConfigurableRegistry() {
      this.register(ClientContext.webhookManager);
      this.register(ClientContext.moduleRegistry);
      this.register(ClientContext.socialRegistry);
      this.register(ClientContext.waypointRegistry);
      this.register(ClientContext.macroRegistry);
      this.register(ClientContext.stashRegistry);
      this.register(ClientContext.hudModuleRegistry);
      this.register(ClientContext.kitRegistry);
      this.register(ClientContext.autoPresetRegistry);
      this.register(ClientContext.aliasRegistry);
      this.register(ClientContext.chatFilterRegistry);
      this.register(ClientContext.glintRegistry);
      this.register(ClientContext.commandRegistry);
      this.register(ClientContext.tradeRegistry);
      this.register(ClientContext.blockColorRegistry);
   }

   @Override
   public Iterator<JsonConfigurable> iterator() {
      return this.configurables.iterator();
   }

   private void register(JsonConfigurable configurable) {
      this.configurables.add(configurable);
   }
}
