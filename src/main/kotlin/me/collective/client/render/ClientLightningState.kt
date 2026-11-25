package me.collective.client.render

interface ClientLightningState {
   fun `collective$isClient`(): Boolean

   fun `collective$setClient`(client: Boolean)
}
