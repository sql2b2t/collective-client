package me.collective.client.events

abstract class LocalPlayerTickPhaseEvent : CancellableEvent() {
    class LocalPlayerTickEvent : LocalPlayerTickPhaseEvent()

    class BeforePassengerCheck : LocalPlayerTickPhaseEvent()
}
