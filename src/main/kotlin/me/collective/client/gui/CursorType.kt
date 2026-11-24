package me.collective.client.gui

import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import java.util.function.Supplier

enum class CursorType(private val cursorSupplier: Supplier<Long>) {
    STANDARD(Supplier { GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR) }),
    POINTER(Supplier { GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR) }),
    INPUT(Supplier { GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR) });

    private var cursorHandle: Long = -1L

    fun getHandle(): Long {
        if (cursorHandle == -1L) {
            cursorHandle = cursorSupplier.get()
        }
        return cursorHandle
    }

    fun apply() {
        if (this != currentCursor) {
            val minecraft = Minecraft.getInstance()
            val window = minecraft.window
            val windowHandle = window.handle()
            val newCursorHandle = getHandle()
            GLFW.glfwSetCursor(windowHandle, newCursorHandle)
            currentCursor = this
        }
    }

    companion object {
        @JvmStatic
        var currentCursor: CursorType = STANDARD
    }
}
