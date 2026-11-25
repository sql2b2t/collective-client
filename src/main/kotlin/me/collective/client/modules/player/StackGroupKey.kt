package me.collective.client.modules.player

import net.minecraft.core.component.DataComponentMap
import net.minecraft.world.item.Item

data class StackGroupKey(
   val item: Item,
   val components: DataComponentMap
)
