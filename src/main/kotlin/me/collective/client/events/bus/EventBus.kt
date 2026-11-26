package me.collective.client.events.bus

import me.collective.client.events.Cancellable
import me.collective.client.events.EventHandler
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.util.ArrayList
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class EventBus : EventDispatcher {
   private val instanceListenerCache: MutableMap<Any, List<EventListener>> =
      Collections.synchronizedMap(IdentityHashMap<Any, List<EventListener>>())
   private val staticListenerCache = ConcurrentHashMap<Class<*>, List<EventListener>>()
   private val listenersByEventClass = ConcurrentHashMap<Class<*>, CopyOnWriteArrayList<EventListener>>()

   override fun subscribe(subscriber: Any) {
      this.subscribeListeners(this.getOrCreateListeners(subscriber.javaClass, subscriber), false)
   }

   override fun unsubscribe(subscriber: Any) {
      this.unsubscribeListeners(this.getOrCreateListeners(subscriber.javaClass, subscriber), false)
      // Иначе IdentityHashMap навсегда удерживает инстанс подписчика (утечка для аддонов/скриптов).
      synchronized(this.instanceListenerCache) {
         this.instanceListenerCache.remove(subscriber)
      }
   }

   override fun subscribeStatic(listenerClass: Class<*>) {
      this.subscribeListeners(this.getOrCreateListeners(listenerClass, null), true)
   }

   override fun unsubscribeStatic(listenerClass: Class<*>) {
      this.unsubscribeListeners(this.getOrCreateListeners(listenerClass, null), true)
      // Иначе ConcurrentHashMap навсегда удерживает Class и его classloader (утечка для аддонов/скриптов).
      this.staticListenerCache.remove(listenerClass)
   }

   private fun getOrCreateListeners(listenerClass: Class<*>, subscriber: Any?): List<EventListener> {
      if (subscriber == null) {
         return this.staticListenerCache.computeIfAbsent(listenerClass) { type -> this.collectListeners(type, null) }
      }
      synchronized(this.instanceListenerCache) {
         return this.instanceListenerCache.computeIfAbsent(subscriber) { this.collectListeners(listenerClass, subscriber) }
      }
   }

   private fun collectListeners(listenerClass: Class<*>, subscriber: Any?): List<EventListener> {
      val listeners = ArrayList<EventListener>()
      var type: Class<*>? = listenerClass
      while (type != null) {
         for (method in type.declaredMethods) {
            if (method.isAnnotationPresent(EventHandler::class.java) &&
         method.returnType == Void.TYPE &&
         method.parameterCount == 1 &&
         !method.parameterTypes[0].isPrimitive
            ) {
               listeners.add(MethodEventListener(type, subscriber, method))
            }
         }
         type = type.superclass
      }
      return listeners.toList()
   }

   private fun subscribeListeners(listeners: List<EventListener>, staticOnly: Boolean) {
      for (listener in listeners) {
         if (!staticOnly || listener.isStatic) {
            this.subscribeListener(listener)
         }
      }
   }

   private fun unsubscribeListeners(listeners: List<EventListener>, staticOnly: Boolean) {
      for (listener in listeners) {
         if (!staticOnly || listener.isStatic) {
            this.unsubscribeListener(listener)
         }
      }
   }

   override fun subscribeListener(listener: EventListener) {
      val listeners = this.listenersByEventClass.computeIfAbsent(
         listener.eventType
      ) { CopyOnWriteArrayList() }
      synchronized(listeners) {
         // Повторная подписка того же инстанса не должна добавлять дубликат.
         if (listeners.contains(listener)) {
            return
         }
         var insertionIndex = 0
         while (insertionIndex < listeners.size &&
         listener.priority <= listeners[insertionIndex].priority
         ) {
            insertionIndex++
         }
         listeners.add(insertionIndex, listener)
      }
   }

   override fun unsubscribeListener(listener: EventListener) {
      val listeners = this.listenersByEventClass[listener.eventType]
      if (listeners != null) {
         listeners.remove(listener)
      }
   }

   private val logger = LoggerFactory.getLogger(EventBus::class.java)

   override fun post(event: Any): Any {
      val listeners = this.listenersByEventClass[event.javaClass]
      if (listeners != null) {
         for (listener in listeners) {
            try {
               listener.call(event)
            } catch (e: Throwable) {
               this.logger.error("Event handler failed for {}: {}", listener, e.message, e)
            }
         }
      }
      return event
   }

   override fun postCancellable(event: Cancellable): Cancellable {
      val listeners = this.listenersByEventClass[event.javaClass]
      if (listeners != null) {
         event.setCancelled(false)
         for (listener in listeners) {
            try {
               listener.call(event)
            } catch (e: Throwable) {
               this.logger.error("Event handler failed for {}: {}", listener, e.message, e)
            }
            if (event.isCancelled) {
               break
            }
         }
      }
      return event
   }
}
