package me.collective.client.access

fun interface VisibleChatLinesAccess {
    fun getVisible(): MutableList<Any?>
}

val VisibleChatLinesAccess.visible: MutableList<Any?>
    get() = getVisible()
