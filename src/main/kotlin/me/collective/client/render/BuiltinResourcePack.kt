package me.collective.client.render

import java.io.InputStream
import java.util.Optional
import me.collective.client.CollectiveClient
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.MetadataSectionType
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.IoSupplier

class BuiltinResourcePack : PackResources {
    internal val location: PackLocationInfo = PackLocationInfo(
        "collective",
        Component.literal(CollectiveClient.NAME),
        PackSource.BUILT_IN,
        Optional.empty(),
    )

    override fun close() {
    }

    override fun getNamespaces(packType: PackType): Set<String> = emptySet()

    override fun listResources(
        packType: PackType,
        namespace: String,
        path: String,
        output: PackResources.ResourceOutput,
    ) {
    }

    override fun <T : Any> getMetadataSection(metadataType: MetadataSectionType<T>): T? = null

    override fun getResource(packType: PackType, location: ResourceLocation): IoSupplier<InputStream>? = null

    override fun location(): PackLocationInfo = location

    override fun getRootResource(vararg path: String): IoSupplier<InputStream>? = null
}
