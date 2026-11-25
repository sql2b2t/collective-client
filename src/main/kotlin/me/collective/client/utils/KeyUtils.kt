package me.collective.client.utils

import me.collective.client.ClientAccess
import me.collective.client.modules.Keybind
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.Window
import java.util.Locale
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW

class KeyUtils private constructor() : ClientAccess {
   companion object {
      private val KEY_ALIASES: Map<String, Int> = mapOf(
         "rshift" to 344,
         "rctrl" to 345,
         "ralt" to GLFW.GLFW_KEY_RIGHT_ALT,
         "shift" to 340,
         "alt" to 342,
         "ctrl" to 341,
         "capslock" to 280
      )

      @JvmStatic
      fun isControlDown(): Boolean {
         // SUPER (Cmd) на macOS — стандартный модификатор для копирования/вставки,
         // поэтому считаем его эквивалентом Ctrl; правые варианты — для rctrl.
         return isPhysicalKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) ||
            isPhysicalKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL) ||
            isPhysicalKeyDown(GLFW.GLFW_KEY_LEFT_SUPER) ||
            isPhysicalKeyDown(GLFW.GLFW_KEY_RIGHT_SUPER)
      }

      @JvmStatic
      fun getKeyAliases(): Map<String, Int> {
         return KEY_ALIASES
      }

      @JvmStatic
      fun isAltDown(): Boolean {
         return isPhysicalKeyDown(GLFW.GLFW_KEY_LEFT_ALT) ||
            isPhysicalKeyDown(GLFW.GLFW_KEY_RIGHT_ALT)
      }

      @JvmStatic
      fun getKeyName(binding: Keybind): String {
         val keyCode = binding.code
         val mouse = binding.mouse
         return getKeyName(keyCode, mouse)
      }

      @JvmStatic
      fun isPhysicallyDown(mapping: KeyMapping): Boolean {
         val key = mapping.key
         return if (key.type == InputConstants.Type.MOUSE) {
            val button = key.value
            isPhysicalMouseButtonDown(button)
         } else {
            val keyCode = key.value
            isPhysicalKeyDown(keyCode)
         }
      }

      @JvmStatic
      fun parseKeybind(bindingText: String): Keybind {
         val lowerText = bindingText.lowercase(Locale.ROOT)

         val mousePrefix = "mouse"
         return if (lowerText.startsWith(mousePrefix)) {
            val digits = lowerText.substring(mousePrefix.length)

            try {
               val mouseButton = digits.toInt()
               Keybind.toggle(mouseButton, true)
            } catch (malformedMouse: NumberFormatException) {
               throw IllegalArgumentException("Unknown mouse key: $digits")
            }
         } else {
            val aliases = KEY_ALIASES
            val aliasCode = aliases[lowerText]
            if (aliasCode != null) {
               val aliasKeyCode = aliasCode
               Keybind.toggle(aliasKeyCode, false)
            } else {
               val normalizedKeyName = lowerText.replace("_", ".")
               val translationKey = "key.keyboard.$normalizedKeyName"
               val key = InputConstants.getKey(translationKey)
               val resolvedCode = key.value
               Keybind.toggle(resolvedCode, false)
            }
         }
      }

      @JvmStatic
      fun isPhysicallyDown(binding: Keybind): Boolean {
         return if (binding.mouse) {
            val button = binding.code
            isPhysicalMouseButtonDown(button)
         } else {
            val keyCode = binding.code
            isPhysicalKeyDown(keyCode)
         }
      }

      @JvmStatic
      fun getKeyName(keyCode: Int, mouse: Boolean): String {
         return if (keyCode < 0) {
            "NONE"
         } else if (mouse) {
            "MOUSE$keyCode"
         } else {
            val key = InputConstants.getKey(keyCode, 0)
            val translationKey = key.name

            val displayName = translationKey.replace(Regex("key.keyboard."), "").replace(".", "_")
            displayName.uppercase(Locale.ROOT)
         }
      }

      @JvmStatic
      fun isPhysicalMouseButtonDown(button: Int): Boolean {
         val window: Window = ClientAccess.collectiveMinecraft.getWindow()
         val handle = window.window
         return GLFW.glfwGetMouseButton(handle, button) == 1
      }

      @JvmStatic
      fun isPhysicalKeyDown(keyCode: Int): Boolean {
         if (keyCode <= 7) {
            return false
         }
         val window: Window = ClientAccess.collectiveMinecraft.getWindow()
         val handle = window.window
         return GLFW.glfwGetKey(handle, keyCode) == 1
      }

      @JvmStatic
      fun isShiftDown(): Boolean {
         return isPhysicalKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) ||
            isPhysicalKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT)
      }
   }
}
