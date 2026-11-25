package me.collective.client.gui.components

import net.minecraft.resources.ResourceKey

@JvmRecord
data class EnchantmentListEntry(
   val enchantmentKey: ResourceKey<*>,
   val level: Int,
   val name: String
)
