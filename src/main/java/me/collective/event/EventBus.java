package me.collective.event;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

public class EventBus {
    public static final EventBus INSTANCE = new EventBus();

    public static class Listener {
        public final Object target;
        public final Method method;
        public final int priority;

        /**
         * Скомпилированный прямой вызов (LambdaMetafactory): обычный вызов
         * метода вместо рефлексии на каждом пакете и тике. Null — рефлексия
         * не сложилась, работаем через method.invoke.
         */
        private final BiConsumer<Object, Object> invoker;

        public Listener(Object target, Method method, int priority) {
            this.target = target;
            this.method = method;
            this.priority = priority;
            this.method.setAccessible(true);
            this.invoker = buildInvoker(method);
        }

        /** Refлексия → прямой вызов: дешёвый hot path пакетного потока. */
        private static BiConsumer<Object, Object> buildInvoker(Method method) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                        method.getDeclaringClass(), MethodHandles.lookup());

                // Реальная сигнатура: (targetType, eventType) → void
                MethodHandle impl = lookup.unreflect(method);

                MethodType interfaceType = MethodType.methodType(BiConsumer.class);
                // SAM-метод интерфейса обязан быть в стёртом виде (Object, Object),
                // иначе лямбда собирается, но кидает AbstractMethodError при вызове
                MethodType erasedSam = MethodType.methodType(void.class, Object.class, Object.class);
                MethodType instantiated = MethodType.methodType(void.class,
                        method.getDeclaringClass(), method.getParameterTypes()[0]);

                CallSite site = LambdaMetafactory.metafactory(
                        lookup,
                        "accept",
                        interfaceType,
                        erasedSam,
                        impl,
                        instantiated);

                @SuppressWarnings("unchecked")
                BiConsumer<Object, Object> consumer = (BiConsumer<Object, Object>) site.getTarget().invokeExact();
                return consumer;
            } catch (Throwable t) {
                return null;
            }
        }

        public void invoke(Object event) {
            try {
                if (this.invoker != null) this.invoker.accept(this.target, event);
                else this.method.invoke(this.target, event);
            } catch (InvocationTargetException e) {
                e.getCause().printStackTrace();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private final Map<Class<?>, List<Listener>> listenersMap = new ConcurrentHashMap<>();

    public void subscribe(Object object) {
        if (object == null) return;

        Class<?> clazz = object.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(EventHandler.class) && method.getParameterCount() == 1) {
                    Class<?> eventType = method.getParameterTypes()[0];
                    EventHandler annotation = method.getAnnotation(EventHandler.class);
                    Listener listener = new Listener(object, method, annotation.priority());

                    listenersMap.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
                    // Sort descending by priority
                    sortListeners(eventType);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    public void unsubscribe(Object object) {
        if (object == null) return;

        for (List<Listener> listeners : listenersMap.values()) {
            listeners.removeIf(l -> l.target == object);
        }
    }

    private synchronized void sortListeners(Class<?> eventType) {
        List<Listener> list = listenersMap.get(eventType);
        if (list != null) {
            List<Listener> sorted = new ArrayList<>(list);
            sorted.sort((a, b) -> Integer.compare(b.priority, a.priority));
            listenersMap.put(eventType, new CopyOnWriteArrayList<>(sorted));
        }
    }

    public void post(Object event) {
        if (event == null) return;

        List<Listener> listeners = listenersMap.get(event.getClass());
        if (listeners != null) {
            for (Listener listener : listeners) {
                listener.invoke(event);
            }
        }
    }
}
