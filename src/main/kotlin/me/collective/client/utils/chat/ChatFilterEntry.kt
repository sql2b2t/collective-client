package me.collective.client.utils.chat

data class ChatFilterEntry(
   val id: String,
   val filter: String
) {
   fun id(): String = this.id

   fun filter(): String = this.filter
}
