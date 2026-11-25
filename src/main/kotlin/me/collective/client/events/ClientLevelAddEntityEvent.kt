package me.collective.client.events

import net.minecraft.world.entity.Entity

class ClientLevelAddEntityEvent(val entity: Entity) : CancellableEvent()
