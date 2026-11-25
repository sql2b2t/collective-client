package me.collective.client.events

class RenderTypeDrawEvent(val stage: EventStage) : CancellableEvent() {
    var override: Int? = null
}
