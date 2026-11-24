package me.collective.client.bots.control

import net.minecraft.core.Registry
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket
import net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket
import net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagNetworkSerialization.NetworkPayload

object BotRegistrySnapshot {
    private const val MAX_RECIPES = 4096
    val recipeEntries: MutableList<ClientboundRecipeBookAddPacket.Entry> = ArrayList()
    @Volatile var updateRecipesPacket: ClientboundUpdateRecipesPacket? = null
    @Volatile var recipeBookSettingsPacket: ClientboundRecipeBookSettingsPacket? = null
    @Volatile var updateAdvancementsPacket: ClientboundUpdateAdvancementsPacket? = null
    @Volatile var selectAdvancementsTabPacket: ClientboundSelectAdvancementsTabPacket? = null
    @Volatile var tagPayloads: Map<ResourceKey<out Registry<*>>, NetworkPayload> = emptyMap()

    fun updateTags(newTags: Map<ResourceKey<out Registry<*>>, NetworkPayload>) {
        val merged = HashMap(tagPayloads)
        merged.putAll(newTags)
        tagPayloads = merged
    }

    fun addRecipes(packet: ClientboundRecipeBookAddPacket) {
        synchronized(recipeEntries) {
            if (packet.replace()) {
                recipeEntries.clear()
            }
            for (entry in packet.entries()) {
                if (recipeEntries.size < MAX_RECIPES) {
                    recipeEntries.add(entry)
                }
            }
        }
    }

    fun removeRecipes(packet: ClientboundRecipeBookRemovePacket) {
        synchronized(recipeEntries) {
            recipeEntries.removeIf { packet.recipes().contains(it.contents().id()) }
        }
    }

    fun buildRecipeBookAddPacket(): ClientboundRecipeBookAddPacket? {
        synchronized(recipeEntries) {
            return if (recipeEntries.isEmpty()) null else ClientboundRecipeBookAddPacket(ArrayList(recipeEntries), true)
        }
    }

    fun clear() {
        updateRecipesPacket = null
        recipeBookSettingsPacket = null
        updateAdvancementsPacket = null
        selectAdvancementsTabPacket = null
        synchronized(recipeEntries) {
            recipeEntries.clear()
        }
    }
}
