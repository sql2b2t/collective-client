package me.collective.client.utils.collections

import net.minecraft.resources.ResourceLocation

class NamedIdentifier : Comparable<NamedIdentifier> {
   val category: String
   val name: String
   val location: ResourceLocation

   constructor(category: String, name: String) {
      this.category = category
      this.name = name
      this.location = ResourceLocation.fromNamespaceAndPath(
         namespaceForCategory(category),
         "sounds/$category/$name.ogg"
      )
   }

   constructor(name: String) : this("system", name)

   val namespace: String
      get() = location.namespace

   val path: String
      get() = location.path

   override fun compareTo(other: NamedIdentifier): Int = location.compareTo(other.location)

   override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is NamedIdentifier) return false
      return location == other.location
   }

   override fun hashCode(): Int = location.hashCode()

   override fun toString(): String = location.toString()

   companion object {
      private fun namespaceForCategory(category: String): String {
         return if (category.equals("system", ignoreCase = true)) "collective" else "collective-mount"
      }

      @JvmField
      val NONE = NamedIdentifier("none")
   }
}
