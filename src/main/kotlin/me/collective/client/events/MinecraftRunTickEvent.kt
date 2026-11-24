package me.collective.client.events

class MinecraftRunTickEvent(@get:JvmName("hasPendingTicks") val hasPendingTicks: Boolean) : CancellableEvent()
