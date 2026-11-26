package me.collective.client.events

class MouseHandlerTurnPlayerEvent(val yawDelta: Double, val pitchDelta: Double) : CancellableEvent()
