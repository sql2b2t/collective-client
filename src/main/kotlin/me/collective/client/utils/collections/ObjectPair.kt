package me.collective.client.utils.collections

data class ObjectPair(
   val first: Any?,
   val second: Any?
) {
   fun first(): Any? = this.first

   fun second(): Any? = this.second
}
