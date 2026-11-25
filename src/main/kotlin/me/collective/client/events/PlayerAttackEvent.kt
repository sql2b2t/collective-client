package me.collective.client.events

import net.minecraft.world.entity.Entity

class PlayerAttackEvent(val stage: EventStage, val target: Entity) : CancellableEvent()
