package me.collective.client.events

import net.minecraft.world.item.ItemStack

class SlotSetEvent(val itemStack: ItemStack) : CancellableEvent()
