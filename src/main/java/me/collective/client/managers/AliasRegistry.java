package me.collective.client.managers;

import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.config.LegacyBranding;
import me.collective.client.modules.Module;
import me.collective.client.settings.JsonConfigurable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;

public final class AliasRegistry implements ClientAccess, JsonConfigurable {
   private final Map<Module, String> moduleAliases = new HashMap<>();
   private final Map<String, String> playerAliases = new HashMap<>();

   public static String getModuleAlias(AliasRegistry registry, Module module) {
      Map<Module, String> aliases = registry.moduleAliases;
      return aliases.getOrDefault(module, module.getName());
   }

   @Override
   public void fromJson(JsonElement input) {
      JsonObject json = input.getAsJsonObject();
      if (json.has("module")) {
         JsonObject moduleObject = json.getAsJsonObject("module");
         moduleObject.asMap().forEach((configuredModuleName, aliasElement) -> {
            Optional<Module> moduleMatch = ClientContext.moduleRegistry.findFirst((Predicate<Module>)module -> {
               return module.getName().equalsIgnoreCase(LegacyBranding.canonicalModuleName(configuredModuleName));
            });
            moduleMatch.ifPresent(module -> {
               String alias = aliasElement.getAsString();
               this.setModuleAlias(module, alias);
            });
         });
      }

      if (json.has("players")) {
         JsonObject playerObject = json.getAsJsonObject("players");
         playerObject.asMap().forEach((playerName, aliasElement) -> {
            String alias = aliasElement.getAsString();
            this.setPlayerAlias(playerName, alias);
         });
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject json = new JsonObject();
      JsonObject moduleObject = new JsonObject();
      JsonObject playerObject = new JsonObject();
      this.moduleAliases.forEach((module, alias) -> {
         String moduleName = module.getName();
         moduleObject.addProperty(moduleName, alias);
      });
      this.playerAliases.forEach(playerObject::addProperty);
      json.add("module", moduleObject);
      json.add("players", playerObject);
      return json;
   }

   @Override
   public String getConfigName() {
      return "aliases.json";
   }

   public void setModuleAlias(Module module, String alias) {
      this.moduleAliases.compute(module, (moduleKey, previousAlias) -> alias);
   }

   public void removePlayerAlias(String playerName) {
      this.playerAliases.remove(playerName);
   }

   public String getPlayerAlias(Player player) {
      GameProfile profile = player.getGameProfile();
      String playerName = profile.name();
      return this.getPlayerAlias(playerName);
   }

   public String getPlayerAlias(String playerName) {
      return this.playerAliases.getOrDefault(playerName, playerName);
   }

   public void setPlayerAlias(String playerName, String alias) {
      this.playerAliases.compute(playerName, (playerNameKey, previousAlias) -> alias);
   }

   public void removeModuleAlias(Module module) {
      this.moduleAliases.remove(module);
   }
}
