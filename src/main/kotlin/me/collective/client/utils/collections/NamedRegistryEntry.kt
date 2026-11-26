package me.collective.client.utils.collections

data class NamedRegistryEntry(
   val name: String,
   val value: Any?
) {
   fun name(): String = this.name

   fun value(): Any? = this.value
}
