package me.collective.client.utils.combat

import me.collective.client.ClientAccess
import me.collective.client.events.ConnectScreenConnectEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.PlayerTotemEvent
import me.collective.client.utils.entity.EntityUtils

import java.util.concurrent.ConcurrentHashMap
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.world.entity.player.Player

class TotemPopTracker : ClientAccess {
   private val popCounts = ConcurrentHashMap<String, Int>()
   private val disconnectTimes = ConcurrentHashMap<String, Long>()

   init {
      ClientAccess.eventBus.subscribe(this)
   }

   fun getPopCount(player: Player): Int {
      val profile = player.gameProfile
      return this.getPopCount(profile.name)
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      val level = ClientAccess.collectiveMinecraft.level ?: return
      val players = level.players().iterator()

      while (players.hasNext()) {
         val player = players.next() as Player

         if (!(EntityUtils.getHealthWithAbsorption(player) > 0.0f)) {
            val playerName = player.name
            val name = playerName.string
            if (popCounts.containsKey(name)) {
               val count = popCounts[name]!!
               popCounts.remove(name)
               val eventBus = ClientAccess.eventBus
               val deathEvent = PlayerTotemEvent(player, count, PlayerTotemEvent.Kind.DEATH)
               eventBus.post(deathEvent)
            }
         }
      }

      val disconnectIterator = disconnectTimes.entries.iterator()

      while (disconnectIterator.hasNext()) {
         val entry = disconnectIterator.next()

         var expiresAt = entry.value

         expiresAt += 120000L
         if (expiresAt < System.currentTimeMillis()) {
            // За тик истекают все записи: раньше удалялась только последняя.
            disconnectIterator.remove()
            popCounts.remove(entry.key)
         }
      }
   }

   @EventHandler
   private fun onConnectScreenConnectEvent(event: ConnectScreenConnectEvent) {
      // Смена сервера не должна переносить счётчики и таймауты между сессиями.
      popCounts.clear()
      disconnectTimes.clear()
   }

   @EventHandler
   private fun onMinecraftDisconnectEvent(event: MinecraftDisconnectEvent) {
      // Сброс при дисконнекте: без него счётчики и таймауты переживают смену мира.
      popCounts.clear()
      disconnectTimes.clear()
   }

   fun getPopCount(name: String): Int {
      return this.popCounts.getOrDefault(name, 0)
   }

   @EventHandler
   private fun onPacketReceive(
      event: PacketEvent.Receive
   ) {
      // Обработчик постится из netty-потока: чтения level/playerInfo — только с клиентского потока.
      ClientAccess.collectiveMinecraft.execute {
         this.processPacket(event)
      }
   }

   private fun processPacket(event: PacketEvent.Receive) {
      if (!isWorldUnavailable()) {
         val level = ClientAccess.collectiveMinecraft.level ?: return
         if (event.packet is ClientboundEntityEventPacket) {
            val entityEventPacket = event.packet as ClientboundEntityEventPacket
            val eventId = entityEventPacket.eventId

            if (eventId == 35.toByte()) {
               val eventEntity = entityEventPacket.getEntity(level)
               if (eventEntity is Player) {
                  val playerName = eventEntity.gameProfile.name
                  val counts = popCounts
                  counts.compute(playerName) { ignoredKey, previousCount ->
                     if (previousCount == null) 1 else previousCount + 1
                  }
                  val eventBus = ClientAccess.eventBus
                  val popEvent = PlayerTotemEvent(eventEntity, popCounts[playerName]!!, PlayerTotemEvent.Kind.TOTEM_POP)
                  eventBus.post(popEvent)
               }
            }
         }

         if (event.packet is ClientboundPlayerInfoUpdatePacket) {
            val playerInfoUpdatePacket = event.packet as ClientboundPlayerInfoUpdatePacket
            val joinedEntries = playerInfoUpdatePacket.newEntries().iterator()

            while (joinedEntries.hasNext()) {
               val joinedEntry = joinedEntries.next()
               val joinedDisconnectTimes = disconnectTimes
               val joinedProfile = joinedEntry.profile() ?: continue
               val joinedName = joinedProfile.name()
               joinedDisconnectTimes.remove(joinedName)
            }
         }

         if (event.packet is ClientboundPlayerInfoRemovePacket) {
            val playerInfoRemovePacket = event.packet as ClientboundPlayerInfoRemovePacket
            val removedProfiles = playerInfoRemovePacket.profileIds().iterator()

            while (removedProfiles.hasNext()) {
               val removedId = removedProfiles.next()
               val listener = ClientAccess.collectiveMinecraft.player?.connection ?: continue
               val playerInfo = listener.getPlayerInfo(removedId)
               if (playerInfo != null) {
                  val removedDisconnectTimes = disconnectTimes
                  val removedProfile = playerInfo.profile
                  val removedName = removedProfile.name
                  val disconnectedAt = System.currentTimeMillis()
                  removedDisconnectTimes[removedName] = disconnectedAt
               }
            }
         }
      }
   }
}
