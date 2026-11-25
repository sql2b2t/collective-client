package me.collective.client.managers

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.authlib.GameProfile
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.config.LegacyBranding
import me.collective.client.modules.Module
import me.collective.client.settings.JsonConfigurable
import net.minecraft.world.entity.player.Player
import java.util.Optional
import java.util.function.Predicate

class AliasRegistry : ClientAccess, JsonConfigurable {
    private val moduleAliases: MutableMap<Module, String> = HashMap()
    private val playerAliases: MutableMap<String, String> = HashMap()

    override fun fromJson(input: JsonElement) {
        val json = input.asJsonObject
        if (json.has("module")) {
            val moduleObject = json.getAsJsonObject("module")
            moduleObject.asMap().forEach { (configuredModuleName, aliasElement) ->
                val moduleMatch: Optional<Module> = ClientContext.moduleRegistry.findFirst(Predicate { module ->
                    module.getName().equals(LegacyBranding.canonicalModuleName(configuredModuleName), ignoreCase = true)
                })
                moduleMatch.ifPresent { module ->
                    setModuleAlias(module, aliasElement.asString)
                }
            }
        }

        if (json.has("players")) {
            val playerObject = json.getAsJsonObject("players")
            playerObject.asMap().forEach { (playerName, aliasElement) ->
                setPlayerAlias(playerName, aliasElement.asString)
            }
        }
    }

    override fun toJson(): JsonElement {
        val json = JsonObject()
        val moduleObject = JsonObject()
        val playerObject = JsonObject()
        moduleAliases.forEach { (module, alias) ->
            moduleObject.addProperty(module.getName(), alias)
        }
        playerAliases.forEach(playerObject::addProperty)
        json.add("module", moduleObject)
        json.add("players", playerObject)
        return json
    }

    override fun getConfigName(): String = "aliases.json"

    fun setModuleAlias(module: Module, alias: String) {
        moduleAliases[module] = alias
    }

    fun removePlayerAlias(playerName: String) {
        playerAliases.remove(playerName)
    }

    fun getPlayerAlias(player: Player?): String {
        if (player == null) {
            return ""
        }
        val profile: GameProfile = player.gameProfile
        return getPlayerAlias(profile.name)
    }

    fun getPlayerAlias(playerName: String): String {
        return playerAliases.getOrDefault(playerName, playerName)
    }

    fun setPlayerAlias(playerName: String, alias: String) {
        playerAliases[playerName] = alias
    }

    fun removeModuleAlias(module: Module) {
        moduleAliases.remove(module)
    }

    companion object {
        @JvmStatic
        fun getModuleAlias(registry: AliasRegistry, module: Module): String {
            return registry.moduleAliases.getOrDefault(module, module.getName())
        }
    }
}
