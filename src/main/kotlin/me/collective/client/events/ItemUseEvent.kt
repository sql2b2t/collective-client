package me.collective.client.events

import net.minecraft.world.item.ItemStack

sealed class ItemUseEvent : CancellableEvent() {
    class BeforeFinish(val stack: ItemStack) : ItemUseEvent()
    class Slowdown : ItemUseEvent()
}
