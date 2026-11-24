package me.collective.client.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Supplier;

public final class IrisShaderBridge {
   private static Supplier<Boolean> handRendererActiveSupplier;
   private static Supplier<Boolean> shaderPackPresentSupplier;

   static {
      handRendererActiveSupplier = null;
      shaderPackPresentSupplier = null;
      initializeHandRendererSupplier();
      initializeShaderPackSupplier();
   }

   public static boolean isHandRendererAvailable() {
      return handRendererActiveSupplier != null;
   }

   private static void initializeHandRendererSupplier() {
      try {
         Lookup lookup = MethodHandles.lookup();

         Class<?> handRendererClass = Class.forName("net.irisshaders.iris.pathways.HandRenderer");
         Class<?>[] parameterTypes = new Class<?>[0];
         Method activeMethod = handRendererClass.getDeclaredMethod("isActive", parameterTypes);
         if (activeMethod.getReturnType() != boolean.class && activeMethod.getReturnType() != Boolean.class) {
            return;
         }

         Field instanceField = handRendererClass.getDeclaredField("INSTANCE");
         Object handRenderer = instanceField.get(null);
         if (handRenderer == null) {
            throw new NullPointerException();
         }

         activeMethod.setAccessible(true);
         MethodHandle activeHandle = lookup.unreflect(activeMethod);
         handRendererActiveSupplier = () -> Boolean.TRUE.equals(invokeOrDefault(activeHandle, handRenderer, false));
      } catch (Exception | LinkageError ignored) {
      }
   }

   public static boolean isShaderPackPresent() {
      return shaderPackPresentSupplier != null && shaderPackPresentSupplier.get();
   }

   public static boolean isHandRendererActive() {
      return handRendererActiveSupplier != null && handRendererActiveSupplier.get();
   }

   private static void initializeShaderPackSupplier() {
      try {
         Lookup lookup = MethodHandles.lookup();

         Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
         Class<?>[] parameterTypes = new Class<?>[0];
         Method currentPackMethod = irisClass.getDeclaredMethod("getCurrentPack", parameterTypes);
         MethodHandle currentPackHandle = lookup.unreflect(currentPackMethod);
         shaderPackPresentSupplier = () -> {
            Object currentPack = invokeOrDefault(currentPackHandle, null, Optional.empty());
            return currentPack instanceof Optional<?> pack && pack.isPresent();
         };
      } catch (Exception | LinkageError ignored) {
      }
   }

   static Object invokeOrDefault(
      MethodHandle handle, Object receiver, Object fallback
   ) {
      try {
         return receiver == null ? (Object)handle.invoke() : (Object)handle.invoke((Object)receiver);
      } catch (Throwable ignored) {
         return fallback;
      }
   }

}
