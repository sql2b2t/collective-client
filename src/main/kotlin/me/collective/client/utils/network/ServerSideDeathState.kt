package me.collective.client.utils.network

interface ServerSideDeathState {
   fun isServerSideDead(): Boolean

   fun setServerSideDead(serverSideDead: Boolean)
}

var ServerSideDeathState.isServerSideDead: Boolean
   get() = isServerSideDead()
   set(value) = setServerSideDead(value)

