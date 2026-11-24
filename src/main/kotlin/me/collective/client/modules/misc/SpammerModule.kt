package me.collective.client.modules.misc

import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.managers.ConfigurationManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.StringSetting

import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Random
import java.util.concurrent.TimeUnit
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MessageSignature
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.protocol.game.ServerboundChatPacket
import org.apache.commons.io.FileUtils

class SpammerModule : Module("Spammer", "Spams messages from a selected text file.", ModuleCategory.MISC) {
   private val pathSetting: Setting<String>
   private val delaySetting: Setting<Float>
   private val refreshSetting: Setting<Boolean>
   private val smartDelaySetting: Setting<Boolean>
   private val randomSetting: Setting<Boolean>
   private val autoDisableSetting: Setting<Boolean>
   private val messageRandom: Random
   private val messages: MutableList<String>
   private val sendTimer: ElapsedTimer
   private var nextMessageIndex: Int = 0

   private fun loadMessages() {
      try {
         var resolvedPath: Path = ConfigurationManager.SPAMMER_DIRECTORY
         val pathSnapshot = this.pathSetting
         val selectedPath = pathSnapshot.value
         resolvedPath = resolvedPath.resolve(selectedPath)
         val baseDirectory = ConfigurationManager.SPAMMER_DIRECTORY.toAbsolutePath().normalize()
         if (!resolvedPath.toAbsolutePath().normalize().startsWith(baseDirectory)) {
            // Защита от path traversal: настройка пути приходит от пользователя,
            // и раньше resolve(абсолютный путь) позволял читать любой файл на диске.
            throw RuntimeException("Invalid spammer file path")
         }
         val filePath = me.collective.client.utils.FileUtils.findExistingWithSuffixes(resolvedPath, ".txt")
         val existenceCheckFile: File = filePath.toFile()
         if (!existenceCheckFile.exists()) {

            throw RuntimeException("Invalid spammer file path")
         }

         this.nextMessageIndex = 0
         this.messages.clear()
         val destinationMessages: MutableList<String> = this.messages
         val readFile: File = filePath.toFile()
         val charset: Charset = StandardCharsets.UTF_8
         destinationMessages.addAll(FileUtils.readLines(readFile, charset))
      } catch (exception: Exception) {
         if (!this.isWorldUnavailable()) {
            val failedPathSnapshot = this.pathSetting

            val errorMessage = "Failed to open spammer file " + failedPathSnapshot.value
            val literalComponent: MutableComponent = Component.literal(errorMessage)
            val errorComponent = literalComponent.withStyle { style: Style ->
               val errorColor = ChatFormatting.RED
               style.withColor(errorColor)
            }
            val exceptionText = exception.toString()
            val exceptionHash = exceptionText.hashCode()

            val errorSignature: MessageSignature = ChatUtils.createSignature(exceptionHash)
            val errorPriority = ChatUtils.MessagePriority.LOW
            ChatUtils.printChat(errorComponent, errorSignature, errorPriority)
         }
      }
   }

   override fun onEnable() {
      this.loadMessages()
   }

   @EventHandler
   private fun onConnectionSendPacketEvent(
      event: PacketEvent.Send
   ) {
      if (event.packet is ServerboundChatPacket) {
         val smartDelaySnapshot = this.smartDelaySetting
         val smartDelayEnabled = smartDelaySnapshot.value
         if (smartDelayEnabled) {
            this.sendTimer.reset()
         }
      }
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      var timerCapture = this.sendTimer
      val delaySnapshot = this.delaySetting
      val delayValue = delaySnapshot.value
      val delaySeconds = delayValue.toDouble()
      val delayUnit = TimeUnit.SECONDS
      if (timerCapture.hasElapsed(delaySeconds, delayUnit) &&
         !this.messages.isEmpty()) {
         val messagesSnapshot = this.messages
         val randomSnapshot = this.randomSetting
         val randomEnabled = randomSnapshot.value
         val selectedIndex: Int
         if (randomEnabled && this.messages.size > 1) {
            val random = this.messageRandom
            // bound = size: раньше size-1 делал последнее сообщение недостижимым.
            val randomBound = this.messages.size
            selectedIndex = random.nextInt(randomBound)
         } else {
            selectedIndex = this.nextMessageIndex
         }

         val message = messagesSnapshot[selectedIndex]
         ChatUtils.sendChatOrCommand(message)

         this.nextMessageIndex = (this.nextMessageIndex + 1) % this.messages.size
         timerCapture = this.sendTimer

         timerCapture.reset()
         val autoDisableSnapshot = this.autoDisableSetting
         val autoDisableEnabled = autoDisableSnapshot.value
         if (autoDisableEnabled) {
            this.toggle()
         }
      }
   }

   init {
      pathSetting = add(StringSetting("Path", "spammer.txt"))
      delaySetting = add(NumberSetting("Delay", 5.0f, 0.0f, 300.0f).withUnit("s"))
      refreshSetting = add(BooleanSetting("Refresh", false))
      smartDelaySetting = add(BooleanSetting("SmartDelay", true))
      randomSetting = add(BooleanSetting("Random", false))
      autoDisableSetting = add(BooleanSetting("AutoDisable", false))
      messageRandom = Random()
      messages = ArrayList()
      sendTimer = ElapsedTimer()
      nextMessageIndex = 0
      pathSetting.setChangeCallback { this.loadMessages() }
      refreshSetting.setChangeCallback {
         val refreshRequested = refreshSetting.value
         if (refreshRequested) {
            refreshSetting.setValue(false)
            this.loadMessages()
         }
      }
   }

}
