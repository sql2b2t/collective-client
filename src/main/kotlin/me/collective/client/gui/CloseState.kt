package me.collective.client.gui

fun interface CloseState {
    fun isClosed(): Boolean

    fun isOpen(): Boolean = !isClosed()

    companion object {
        @JvmField
        val ALWAYS_OPEN: CloseState = CloseState { false }
    }
}
