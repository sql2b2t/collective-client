package me.collective.client.settings

fun interface ValueProvider<T> {
    fun getValue(): T
}
