package me.collective.client.events

import net.minecraft.world.inventory.Slot

class AbstractContainerMenuMoveItemStackToEvent(val slot: Slot) : CancellableEvent()
