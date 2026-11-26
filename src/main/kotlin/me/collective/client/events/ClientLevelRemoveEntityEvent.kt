package me.collective.client.events

class ClientLevelRemoveEntityEvent(val id: Int, val isEntityPresent: Boolean) : CancellableEvent()
