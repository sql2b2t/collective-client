package me.collective.client.events

class InputPressEvent(val code: Int, val isMouse: Boolean) : CancellableEvent()
