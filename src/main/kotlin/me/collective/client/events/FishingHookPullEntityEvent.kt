package me.collective.client.events

import net.minecraft.world.entity.Entity

class FishingHookPullEntityEvent(val entity: Entity) : CancellableEvent()
