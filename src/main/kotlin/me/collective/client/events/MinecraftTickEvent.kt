package me.collective.client.events

class MinecraftTickEvent private constructor() : CancellableEvent() {
    companion object {
        @JvmField
        val INSTANCE: MinecraftTickEvent = MinecraftTickEvent()
    }
}
