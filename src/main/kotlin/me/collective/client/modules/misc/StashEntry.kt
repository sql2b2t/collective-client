package me.collective.client.modules.misc

import com.google.gson.annotations.SerializedName
import me.collective.client.settings.NamedEntry

class StashEntry(
    @field:SerializedName("id")
    private val id: String,
    @field:SerializedName("x")
    private val x: Int,
    @field:SerializedName("z")
    private val z: Int,
    @field:SerializedName("dimension")
    private val dimension: String,
    @field:SerializedName("server")
    private val server: String
) : NamedEntry {
    override fun getName(): String {
        return this.id
    }

    fun formatCoordinates(): String {
        return "$x, $z."
    }

    fun getX(): Double {
        return this.x.toDouble()
    }

    fun getZ(): Double {
        return this.z.toDouble()
    }

    fun getDimension(): String {
        return this.dimension
    }

    fun getServer(): String {
        return this.server
    }
}
