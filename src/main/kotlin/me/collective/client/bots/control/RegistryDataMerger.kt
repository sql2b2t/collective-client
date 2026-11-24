package me.collective.client.bots.control

import net.minecraft.client.multiplayer.ClientRegistryLayer
import net.minecraft.client.multiplayer.RegistryDataCollector
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess.Frozen
import net.minecraft.core.RegistrySynchronization.PackedRegistryEntry
import net.minecraft.resources.ResourceKey
import net.minecraft.server.packs.resources.ResourceProvider
import net.minecraft.tags.TagNetworkSerialization.NetworkPayload

object RegistryDataMerger {

    fun mergeRegistries(
        registryData: Map<ResourceKey<out Registry<*>>, List<PackedRegistryEntry>>,
        tags: Map<ResourceKey<out Registry<*>>, NetworkPayload>
    ): Frozen {
        val collector = RegistryDataCollector()
        registryData.forEach { (key, entries) ->
            collector.appendContents(key, entries)
        }
        collector.appendTags(tags)
        val initialAccess = ClientRegistryLayer.createRegistryAccess().compositeAccess()
        return collector.collectGameRegistries(ResourceProvider.EMPTY, initialAccess, false)
    }
}
