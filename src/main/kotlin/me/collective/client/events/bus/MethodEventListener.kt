package me.collective.client.events.bus

import me.collective.client.events.EventHandler
import java.lang.invoke.LambdaMetafactory
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.function.Consumer

class MethodEventListener(declaringClass: Class<*>, subscriber: Any?, handlerMethod: Method) : EventListener {
   override val eventType: Class<*> = handlerMethod.parameterTypes[0]
   override val isStatic: Boolean = Modifier.isStatic(handlerMethod.modifiers)
   override val priority: Int = handlerMethod.getAnnotation(EventHandler::class.java).priority
   private val consumer: Consumer<Any?>

   init {
      try {
         val lookup: MethodHandles.Lookup = MethodHandles.privateLookupIn(declaringClass, MethodHandles.lookup())
         val handlerType = MethodType.methodType(Void.TYPE, this.eventType)
         val implementation = if (this.isStatic)
            lookup.findStatic(declaringClass, handlerMethod.name, handlerType)
         else
            lookup.findVirtual(declaringClass, handlerMethod.name, handlerType)
         val factoryType = if (this.isStatic)
            MethodType.methodType(Consumer::class.java)
         else
            MethodType.methodType(Consumer::class.java, declaringClass)
         val factory = LambdaMetafactory.metafactory(
            lookup, "accept", factoryType,
            MethodType.methodType(Void.TYPE, Any::class.java), implementation, handlerType
         ).target
         this.consumer = if (this.isStatic)
            @Suppress("UNCHECKED_CAST") factory.invoke() as Consumer<Any?>
         else
            @Suppress("UNCHECKED_CAST") factory.invoke(subscriber) as Consumer<Any?>
      } catch (failure: Throwable) {
         throw IllegalStateException("Cannot register event handler " + handlerMethod, failure)
      }
   }

   override fun call(event: Any?) {
      this.consumer.accept(event)
   }
}
