package me.collective.client.utils.world

import me.collective.client.managers.CommandRegistry
import me.collective.client.settings.NamedEntry
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.math.MathUtils

import com.google.gson.annotations.SerializedName
import java.util.function.Supplier
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent.RunCommand
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent.ShowText
import net.minecraft.network.chat.Style
import net.minecraft.world.phys.Vec3

class Waypoint(
   name: String,
   x: Double,
   y: Double,
   z: Double,
   dimension: String,
   server: String
) : NamedEntry {
   private var cachedPosition: Vec3? = Vec3(x, y, z)

   @field:SerializedName("x")
   val x: Double = roundCoordinate(x)

   @field:SerializedName("y")
   val y: Double = roundCoordinate(y)

   @field:SerializedName("z")
   val z: Double = roundCoordinate(z)

   @get:JvmName("waypointName")
   @field:SerializedName("name")
   val name: String = name

   override fun getName(): String = this.name

   @field:SerializedName("dimension")
   val dimension: String = dimension

   @field:SerializedName("server")
   val server: String = server

   @field:SerializedName("toggled")
   var toggled: Boolean = true

   constructor(name: String, position: Vec3, dimension: String, server: String) : this(
      name, position.x, position.y, position.z, dimension, server
   )

   fun getPosition(): Vec3 {
      var cached = cachedPosition
      if (cached == null) {
         cached = Vec3(x, y, z)
         cachedPosition = cached
      }

      return cached
   }

   fun distanceTo(position: Vec3): Double {
      val waypointPosition = getPosition()
      return position.distanceTo(waypointPosition)
   }

   private fun getDisplayColor(): Int {
      return if (toggled) -1 else 0xAAAAAA!!
   }

   fun roundCoordinate(coordinate: Double): Double {
      val decimalPlaces = 1
      return MathUtils.floorToDecimals(coordinate, decimalPlaces)
   }

   override fun getDisplayComponent(): Component {
      val infoText = String.format(
         "%s's info:\nx: %.1f\ny: %.1f\nz: %.1f\ndimension: %s\nserver: %s\ntoggled: %b",
         name, x, y, z, dimension, server, toggled
      )
      val toggleCommand = String.format("%swaypoints toggle %s", CommandRegistry.getPrefix(), name)
      val hoverEvent = ShowText(
         Component.literal(infoText).withStyle { hoverStyle -> hoverStyle.applyFormat(ChatFormatting.GRAY) }
      )
      val clickEvent = RunCommand(toggleCommand)
      return Component.literal(name).withStyle { nameStyle ->
         val hoveredStyle = nameStyle.withHoverEvent(hoverEvent)
         val clickedStyle = hoveredStyle.withClickEvent(clickEvent)
         ChatUtils.withDynamicColor(clickedStyle, Supplier { getDisplayColor() })
      }
   }

   fun matches(requestedName: String, requestedServer: String): Boolean {
      if (name == requestedName) {
         if (server.equals(requestedServer, ignoreCase = true)) {
            return true
         }
      }

      return false
   }
}
