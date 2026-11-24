package me.collective.client.config

import java.io.IOException
import java.util.ArrayDeque
import java.util.Deque

class PresetUndoState {
    private val actions: Deque<PresetUndoAction> = ArrayDeque()

    fun push(action: PresetUndoAction?) {
        if (action == null) {
            return
        }
        this.actions.addLast(action)
        while (this.actions.size > HISTORY_LIMIT) {
            this.actions.removeFirst()
        }
    }

    @Throws(IOException::class)
    fun undo(): PresetUndoAction? {
        val action = this.actions.peekLast()
        if (action != null) {
            action.undo()
            this.actions.removeLast()
        }
        return action
    }

    companion object {
        private const val HISTORY_LIMIT = 5
    }
}
