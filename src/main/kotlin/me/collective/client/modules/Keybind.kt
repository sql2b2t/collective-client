package me.collective.client.modules

import me.collective.client.settings.NamedEntry
import me.collective.client.utils.KeyUtils

data class Keybind(
    @get:JvmName("getCodeField") val code: Int,
    @get:JvmName("getModeField") val mode: Mode,
    @get:JvmName("getMouseField") val mouse: Boolean
) {
    fun code(): Int = code
    fun mode(): Mode = mode
    fun mouse(): Boolean = mouse
    fun getCode(): Int = code
    fun getMode(): Mode = mode
    fun isMouse(): Boolean = mouse
    fun getDisplayName(): String = displayName()

    val isUnbound: Boolean @JvmName("isUnboundProp") get() = isUnbound()
    val displayName: String @JvmName("displayNameProp") get() = displayName()

    fun isUnbound(): Boolean = this.code < 0

    fun withMode(mode: Mode): Keybind = Keybind(this.code, mode, this.mouse)

    fun withCode(code: Int): Keybind = Keybind(code, this.mode, this.mouse)

    fun withMouse(mouse: Boolean): Keybind = Keybind(this.code, this.mode, mouse)

    fun displayName(): String = KeyUtils.getKeyName(this)

    enum class Mode(val displayName: String, val serializedName: String) : NamedEntry {
        TOGGLE("Toggle", "toggle"),
        HOLD("Hold", "hold"),
        HOLD_REVERSE("ReverseHold", "hold_reverse");

        override fun getName(): String = displayName

        companion object {
            @JvmStatic
            fun fromSerializedName(value: String): Mode {
                return when (value.trim().lowercase()) {
                    "hold" -> HOLD
                    "hold_reverse" -> HOLD_REVERSE
                    else -> TOGGLE
                }
            }
        }
    }

    companion object {
        @JvmField
        val UNBOUND: Keybind = Keybind(-1, Mode.TOGGLE, false)

        @JvmStatic
        fun toggle(code: Int, mouse: Boolean): Keybind = Keybind(code, Mode.TOGGLE, mouse)
    }
}
