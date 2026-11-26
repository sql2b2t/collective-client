package me.collective.client.utils.collections

import net.minecraft.resources.Identifier

class NamedIdentifier : Identifier {
   val category: String
   val name: String

   constructor(category: String, name: String) : super(
      namespaceForCategory(category),
      "sounds/$category/$name.ogg"
   ) {
      this.category = category
      this.name = name
   }

   constructor(name: String) : this("system", name)

   companion object {
      private fun namespaceForCategory(category: String): String {
         return if (category.equals("system", ignoreCase = true)) "collective" else "collective-mount"
      }

      @JvmField
      val NONE = NamedIdentifier("none")
   }
}
