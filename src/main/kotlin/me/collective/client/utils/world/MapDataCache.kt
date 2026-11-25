package me.collective.client.utils.world

import me.collective.client.managers.ConfigurationManager
import me.collective.client.render.FontRenderer
import me.collective.client.utils.CompressionUtils
import me.collective.client.utils.FileUtils

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ConnectScreenConnectEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.events.PacketEvent
import me.collective.client.utils.ElapsedTimer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.MapItem
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.level.saveddata.maps.MapItemSavedData

class MapDataCache : ClientAccess {
   private val saveTimer = ElapsedTimer()
   private val mapDataByName: MutableMap<String, MapItemSavedData> = Collections.synchronizedMap(java.util.HashMap())

   private var serverAddress: String? = null

   init {
      ClientAccess.eventBus.subscribe(this)
   }

   fun load(address: String) {
      mapDataByName.clear()
      val filePath = ConfigurationManager.MAPS_DIRECTORY.resolve(FontRenderer.sanitizeFileName(address) + ".data")
      if (!filePath.toFile().exists()) {
         return
      }

      val decodedBytes: ByteArray
      try {
         val fileBytes = FileUtils.readBytes(filePath)
         decodedBytes = CompressionUtils.inflate(fileBytes)
      } catch (decodeFailure: Exception) {
         return
      }

      val byteInput = ByteArrayInputStream(decodedBytes)
      try {
         DataInputStream(byteInput).use { input ->
            val entryCount = input.readInt()
            for (entryIndex in 0 until entryCount) {
               val name = input.readUTF()
               val scale = input.readByte()
               val mapData = MapItemSavedData.createForClient(scale, true, Level.OVERWORLD)
               for (colorIndex in 0 until 16384) {
                  mapData.colors[colorIndex] = input.readByte()
               }
               mapDataByName[name] = mapData
            }
         }
      } catch (loadFailure: Exception) {
         loadFailure.printStackTrace()
      }
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      if (saveTimer.hasElapsedAndReset(5L, TimeUnit.MINUTES) && serverAddress != null) {
         CompletableFuture.runAsync({
            val address = serverAddress!!
            save(address)
         }, ClientAccess.executorService)
      }
   }

   @EventHandler
   private fun onConnectScreenConnectEvent(event: ConnectScreenConnectEvent) {
      val address = event.serverAddress
      serverAddress = address
      load(address)
   }

   @EventHandler
   private fun onMinecraftDisconnectEvent(event: MinecraftDisconnectEvent) {
      val address = serverAddress
      if (address != null) {
         save(address)
      }
   }

   fun getMapData(stack: ItemStack, mapId: MapId): MapItemSavedData? {
      val level = ClientAccess.collectiveMinecraft.level!!
      val worldData = level?.let { MapItem.getSavedData(mapId, it) }
      if (mapId.id != -1 && worldData != null) {
         return worldData
      }

      val name = stack.hoverName.string
      return if (stack.has(DataComponents.CUSTOM_NAME)) mapDataByName[name] else null
   }

   fun save(address: String) {
      if (mapDataByName.isEmpty()) {
         return
      }

      val byteOutput = ByteArrayOutputStream()
      try {
         DataOutputStream(byteOutput).use { output ->
            val entrySnapshot: Set<Map.Entry<String, MapItemSavedData>>
            synchronized(mapDataByName) {
               entrySnapshot = HashSet(mapDataByName.entries)
            }
            val entryCount = entrySnapshot.size
            output.writeInt(entryCount)
            for (entry in entrySnapshot) {
               val name = entry.key
               output.writeUTF(name)
               val scale = entry.value.scale
               output.writeByte(scale.toInt())
               for (color in entry.value.colors) {
                  output.writeByte(color.toInt())
               }
            }

            val filePath = ConfigurationManager.MAPS_DIRECTORY.resolve(FontRenderer.sanitizeFileName(address) + ".data")
            val serializedBytes = byteOutput.toByteArray()
            FileUtils.writeBytes(filePath, CompressionUtils.deflate(serializedBytes, 2))
         }
      } catch (saveFailure: Exception) {
         saveFailure.printStackTrace()
      }
   }

   fun getMapData(stack: ItemStack, mapId: Int): MapItemSavedData? {
      val lookupId = MapId(mapId)
      return getMapData(stack, lookupId)
   }

   private fun cacheNamedMap(stack: ItemStack) {
      if (stack.item === Items.FILLED_MAP && stack.has(DataComponents.CUSTOM_NAME)) {
         val name = stack.hoverName.string
         val mapId = stack.getOrDefault(DataComponents.MAP_ID, MapId(-1))
         mapDataByName.compute(name) { _, _ ->
            val level = ClientAccess.collectiveMinecraft.level!!
            level?.let { MapItem.getSavedData(mapId, it) }
         }
      }
   }

   @EventHandler
   private fun onPacketReceive(event: PacketEvent.Receive) {
      if (event.packet is ClientboundMapItemDataPacket) {
         val scheduler = ClientContext.taskScheduler
         val updateMaps = Runnable {
            val player = ClientAccess.collectiveMinecraft.player ?: return@Runnable
            val inventory = player.inventory
            for (stack in inventory.items) {
               cacheNamedMap(stack)
            }
         }
         scheduler.scheduleRotationTask(updateMaps, 1)
      }
   }
}
