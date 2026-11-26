package me.collective.client.events

class AuraAttackEvent(val stage: EventStage, val entityId: Int) : CancellableEvent()
