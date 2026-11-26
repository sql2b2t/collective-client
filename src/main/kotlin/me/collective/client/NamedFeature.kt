package me.collective.client

import me.collective.client.settings.NamedEntry
import java.util.Objects

open class NamedFeature(@JvmField val name: String) : NamedEntry {
    private var _description: String = ""

    var description: String
        @JvmName("getDescriptionProp") get() = getDescription()
        @JvmName("setDescriptionProp") set(value) = setDescription(value)

    val isWorldUnavailable: Boolean
        @JvmName("isWorldUnavailableProp")
        get() = ClientAccess.collectiveMinecraft.player == null || ClientAccess.collectiveMinecraft.level == null

    override fun getName(): String = this.name

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this.javaClass != other.javaClass) {
            return false
        }
        val feature = other as NamedFeature
        return this.name == feature.name
    }

    override fun hashCode(): Int = Objects.hash(this.name)

    open fun setDescription(description: String) {
        this._description = description
    }

    open fun getDescription(): String = this._description

    open fun isDescriptionPresent(): Boolean = this._description != ""
}
