package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

enum class AntiPhaseMaterialChoice(private val displayName: String, val item: Item) : NamedEntry {
    LADDER("Ladder", Items.LADDER),
    VINE("Vine", Items.VINE),
    SCAFFOLDING("Scaffolding", Items.SCAFFOLDING),
    ITEMFRAME("ItemFrame", Items.ITEM_FRAME);

    override fun getName(): String = displayName
}
