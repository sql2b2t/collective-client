package me.collective.client.events

class StepHeightEvent(
    val stage: EventStage,
    height: Float
) {
    var height: Float = height
        set(value) {
            if (field != value) {
                field = value
                isModified = true
            }
        }

    var isModified: Boolean = false
        private set
}
