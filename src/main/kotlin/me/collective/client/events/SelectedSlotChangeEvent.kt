package me.collective.client.events

open class SelectedSlotChangeEvent(val slot: Int) : CancellableEvent() {
    class ClientPacketListenerHandleSetHeldSlotEvent(val slot: Int) : CancellableEvent()
}
