package me.collective.client.modules.misc

import me.collective.client.ClientAccess
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.managers.ConfigurationManager
import me.collective.client.utils.world.WorldUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.DimensionType

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.stream.Collectors
import java.util.stream.Stream
import net.minecraft.client.KeyboardHandler
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket

class CoordLoggerModule : Module("CoordLogger", "Saves your coordinates whenever you log/die.", ModuleCategory.MISC) {
   private val disconnectSetting: Setting<Boolean>
   private val deathSetting: Setting<Boolean>
   private val fileSetting: Setting<Boolean>
   @Volatile
   private var coordinateText: String? = null

   private fun appendRetainedEntry(entry: String) {
      val directory: Path = ConfigurationManager.ROOT_DIRECTORY

      val logFile = directory.resolve("coordlogger.txt")

      try {
         val existingLines: Stream<String>
         if (Files.exists(logFile)) {
            existingLines = Files.lines(logFile)
         } else {
            existingLines = Stream.empty()
         }

         existingLines.use {
            val filteredLines = Stream.concat(existingLines, Stream.of(entry)).filter { candidate: String -> this.isRetainedEntry(candidate) }
            val retainedLines = filteredLines.collect(Collectors.toList())
            Files.write(logFile, retainedLines)
         }
      } catch (ignoredIOException: IOException) {
      }
   }

   private fun isRetainedEntry(entry: String): Boolean {
      if (entry == null || entry.isBlank()) {
         return false
      }
      val fields = entry.split(Regex("\\|"))
      val fieldCount = fields.size

      if (fieldCount != (3)) {
         return false
      }

      return try {
         val now = LocalDateTime.now()

         val timestampText = fields[2]
         val timestampSnapshot = timestampText.trim()
         val parserFormatter = timestampFormatter
         val entryTime = LocalDateTime.parse(timestampSnapshot, parserFormatter)
         val ageUnit = ChronoUnit.DAYS
         val ageDays = ageUnit.between(entryTime, now)

         ageDays < 7L
      } catch (invalidTimestamp: DateTimeParseException) {
         false
      }
   }

   private fun saveCoordinates(reason: String) {
      if (this.coordinateText != null) {
         val entry = StringBuilder().append(this.coordinateText).append(" | ").append(reason)
         val keyboard: KeyboardHandler = ClientAccess.collectiveMinecraft.keyboardHandler
         val clipboardText = entry.toString()
         keyboard.setClipboard(clipboardText)
         val writeFile = this.fileSetting.value
         if (writeFile) {
            val now = LocalDateTime.now()
            val formatter = timestampFormatter
            val timestamp = now.format(formatter)
            entry.append(" | ").append(timestamp)
            this.appendRetainedEntry(entry.toString())
         }
      }
   }

   @EventHandler
   private fun onDisconnect(event: MinecraftDisconnectEvent) {
      if (ClientAccess.collectiveMinecraft.player != null) {
         val disconnectSettingSnapshot = this.disconnectSetting
         val saveOnDisconnect = disconnectSettingSnapshot.value
         if (saveOnDisconnect) {

            this.saveCoordinates("Disconnect")
            return
         }
      }
   }

   @EventHandler
   private fun onPlayerTick(event: LocalPlayerTickEvent) {

      val xPlayer = ClientAccess.collectiveMinecraft.player!!
      val blockX = xPlayer!!.blockX
      val yPlayer = ClientAccess.collectiveMinecraft.player!!
      val blockY = yPlayer!!.blockY

      val zPlayer = ClientAccess.collectiveMinecraft.player!!
      val blockZ = zPlayer!!.blockZ

      val dimension: DimensionType = WorldUtils.getDimension()
      this.coordinateText = "X: $blockX, Y: $blockY, Z: $blockZ in The ${dimension.getDisplayName()}"
   }

   @EventHandler
   private fun onPacketReceive(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundPlayerCombatKillPacket) {
         val deathSettingSnapshot = this.deathSetting
         val saveOnDeath = deathSettingSnapshot.value
         if (saveOnDeath) {

            this.saveCoordinates("Death")
         }
      }
   }

   init {
      disconnectSetting = add(BooleanSetting("Log", true))
      deathSetting = add(BooleanSetting("Death", true))
      fileSetting = add(BooleanSetting("File", false))
   }

   companion object {

      private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(("HH:mm dd-MM-yyyy"))
   }

}
