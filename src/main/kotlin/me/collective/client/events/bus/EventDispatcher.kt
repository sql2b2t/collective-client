package me.collective.client.events.bus

import me.collective.client.events.Cancellable

interface EventDispatcher {
   fun subscribe(subscriber: Any)

   fun unsubscribeStatic(listenerClass: Class<*>)

   fun unsubscribe(subscriber: Any)

   fun subscribeListener(listener: EventListener)

   fun postCancellable(event: Cancellable): Cancellable

   fun unsubscribeListener(listener: EventListener)

   fun post(event: Any): Any

   fun subscribeStatic(listenerClass: Class<*>)
}
