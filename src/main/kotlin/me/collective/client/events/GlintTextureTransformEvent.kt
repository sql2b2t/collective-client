package me.collective.client.events

class GlintTextureTransformEvent : CancellableEvent() {
    var scale: Float = 1.0f

    companion object {
        private val INSTANCE = GlintTextureTransformEvent()

        @JvmStatic
        fun resetAndGet(): GlintTextureTransformEvent {
            INSTANCE.scale = 1.0f
            return INSTANCE
        }
    }
}
