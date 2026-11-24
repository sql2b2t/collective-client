package me.collective.client.utils

import me.collective.client.ClientAccess
import java.net.SocketAddress
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.Connection

class ServerAddressUtils private constructor() {
   companion object {
      private const val fallbackServerAddress = "singleplayer"

      @JvmStatic
      fun getCurrentServerAddress(): String {
         var address: String? = null

         try {
            val packetListener: ClientPacketListener = ClientAccess.collectiveMinecraft.getConnection()!!
            val connection: Connection = packetListener.getConnection()
            val remoteAddress: SocketAddress = connection.getRemoteAddress()
            var addr: String = remoteAddress.toString()

            val separatorIndex = addr.indexOf(':')
            if (separatorIndex > 0) {
               addr = addr.substring(0, separatorIndex)
            }

            while (addr.endsWith(".")) {
               addr = addr.substring(0, addr.length - 1)
            }

            address = addr
         } catch (ignoredFailure: Throwable) {
         }

         return if (address != null && !address.isEmpty()) address else fallbackServerAddress
      }
   }
}
