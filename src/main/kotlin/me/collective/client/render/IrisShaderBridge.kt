package me.collective.client.render

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.util.Optional

object IrisShaderBridge {
    private var handRendererActiveSupplier: (() -> Boolean)? = null
    private var shaderPackPresentSupplier: (() -> Boolean)? = null

    init {
        initializeHandRendererSupplier()
        initializeShaderPackSupplier()
    }

    @JvmStatic
    fun isHandRendererAvailable(): Boolean = handRendererActiveSupplier != null

    private fun initializeHandRendererSupplier() {
        try {
            val lookup = MethodHandles.lookup()
            val handRendererClass = Class.forName("net.irisshaders.iris.pathways.HandRenderer")
            val activeMethod = handRendererClass.getDeclaredMethod("isActive")
            if (activeMethod.returnType != Boolean::class.javaPrimitiveType &&
                activeMethod.returnType != Boolean::class.javaObjectType
            ) {
                return
            }

            val instanceField = handRendererClass.getDeclaredField("INSTANCE")
            val handRenderer = instanceField.get(null) ?: throw NullPointerException()

            activeMethod.isAccessible = true
            val activeHandle = lookup.unreflect(activeMethod)
            handRendererActiveSupplier = {
                invokeOrDefault(activeHandle, handRenderer, false) == true
            }
        } catch (_: Exception) {
        } catch (_: LinkageError) {
        }
    }

    @JvmStatic
    fun isShaderPackPresent(): Boolean = shaderPackPresentSupplier?.invoke() == true

    @JvmStatic
    fun isHandRendererActive(): Boolean = handRendererActiveSupplier?.invoke() == true

    private fun initializeShaderPackSupplier() {
        try {
            val lookup = MethodHandles.lookup()
            val irisClass = Class.forName("net.irisshaders.iris.Iris")
            val currentPackMethod = irisClass.getDeclaredMethod("getCurrentPack")
            val currentPackHandle = lookup.unreflect(currentPackMethod)
            shaderPackPresentSupplier = {
                val currentPack = invokeOrDefault(currentPackHandle, null, Optional.empty<Any>())
                currentPack is Optional<*> && currentPack.isPresent
            }
        } catch (_: Exception) {
        } catch (_: LinkageError) {
        }
    }

    @JvmStatic
    internal fun invokeOrDefault(handle: MethodHandle, receiver: Any?, fallback: Any?): Any? {
        return try {
            if (receiver == null) handle.invoke() else handle.invoke(receiver)
        } catch (_: Throwable) {
            fallback
        }
    }
}
