package me.collective.client.utils.network

interface ImportantPacket {
   fun `collective$isImportant`(): Boolean

   fun `collective$markImportant`()
}
