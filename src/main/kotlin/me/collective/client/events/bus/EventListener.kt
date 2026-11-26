package me.collective.client.events.bus

interface EventListener {
   val isStatic: Boolean

   val eventType: Class<*>

   val priority: Int

   fun call(event: Any?)
}
