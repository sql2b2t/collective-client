package me.collective.client.modules.misc

import me.collective.client.ClientContext
import me.collective.client.managers.ConfigurationManager
import me.collective.client.managers.SocialRegistry
import me.collective.client.settings.Setting
import me.collective.client.utils.chat.ChatUtils

import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.ThreadLocalRandom
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MessageSignature
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.world.entity.player.Player
import org.apache.commons.io.FileUtils

class KillSayMessages(private val module: KillEffectsModule) {
   private val lines: MutableList<String> = Collections.synchronizedList(ArrayList())
   private var lineIndex: Int = 0

   private fun nextMessage(): String? {
      return try {
         if (this.lines.isEmpty()) {
            null
         } else {
            val randomOption: Setting<Boolean> = this.module.randomSetting
            val random: Boolean = randomOption.value
            if (random) {
               val messageLines = this.lines
               val randomSnapshot = ThreadLocalRandom.current()
               val lineCount = this.lines.size
               messageLines[randomSnapshot.nextInt(lineCount)]
            } else {
               this.lines[
                  this.lineIndex++
                     % this.lines.size
               ]
            }
         }
      } catch (selectionFailure: Exception) {
         null
      }
   }

   fun resetSequence() {
      this.lineIndex = 0
   }

   fun sendKillMessage(player: Player, playerName: String) {
      val killSayOption: Setting<Boolean> = this.module.killSaySetting
      val killSay: Boolean = killSayOption.value
      if (killSay) {
         val onlyEnemiesOption: Setting<Boolean> = this.module.onlyEnemiesSetting
         val onlyEnemies: Boolean = onlyEnemiesOption.value
         if (onlyEnemies) {
            val enemyManagerSnapshot: SocialRegistry = ClientContext.socialRegistry

            val enemyPlayer = player
            if (!enemyManagerSnapshot.isEnemyPlayer(enemyPlayer)) {
               return
            }
         }

         val nextMessage = this.nextMessage()
         if (nextMessage != null) {

            val message = nextMessage.replace("{name}", playerName)

            val messageToSend = message
            val sendMessage = Runnable {

               ChatUtils.sendChatOrCommand(messageToSend)
            }
            val delayOptionSnapshot: Setting<Float> = this.module.delaySetting

            if (delayOptionSnapshot.isAtMinimum()) {
               sendMessage.run()
            } else {
               val scheduler = ClientContext.taskScheduler
               val delayReadOption: Setting<Float> = this.module.delaySetting
               val delaySeconds: Float = delayReadOption.value

               val delayTicks = (delaySeconds / 0.05f).toInt()
               scheduler.scheduleRotationTask(sendMessage, delayTicks)
            }
         }
      }
   }

   fun reloadMessages(force: Boolean) {
      val killSayOption: Setting<Boolean> = this.module.killSaySetting
      val killSay: Boolean = killSayOption.value
      if (killSay || force) {
         var resolvedPath: Path = ConfigurationManager.KILLSAY_DIRECTORY
         val pathOption: Setting<String> = this.module.pathSetting
         resolvedPath = resolvedPath.resolve(pathOption.value)
         val killsayBaseDirectory = ConfigurationManager.KILLSAY_DIRECTORY.toAbsolutePath().normalize()
         if (!resolvedPath.toAbsolutePath().normalize().startsWith(killsayBaseDirectory)) {
            // Защита от path traversal в настройке пути (resolve абсолютного пути обходил каталог).
            this.lines.clear()
            return
         }
         val extensions = arrayOf(".txt")

         val path = me.collective.client.utils.FileUtils.findExistingWithSuffixes(resolvedPath, *extensions)

         try {
            val checkFile: File = path.toFile()
            if (!checkFile.exists()) {

               val missingPathOption: Setting<String> = this.module.pathSetting
               throw IOException(String.format("KillSay file %s not found", missingPathOption.value))
            }

            this.lines.clear()
            val messageLines = this.lines
            val readFileSnapshot: File = path.toFile()
            val encoding: Charset = StandardCharsets.UTF_8
            messageLines.addAll(FileUtils.readLines(readFileSnapshot, encoding))
            this.lineIndex = 0
         } catch (loadFailure: Exception) {
            val failureKillSayOption: Setting<Boolean> = this.module.killSaySetting
            val showFailure: Boolean = failureKillSayOption.value
            if (showFailure) {
               val failureMessage = "Failed to update KillSay lines: $loadFailure"
               var notification: MutableComponent = Component.literal(failureMessage)
               notification = notification.withStyle { notificationStyle: Style ->
                  val notificationColor = ChatFormatting.RED
                  notificationStyle.withColor(notificationColor)
               }

               val signature: MessageSignature = ChatUtils.createSignature(-9634482)
               val priority = ChatUtils.MessagePriority.LOW
               ChatUtils.printChat(notification, signature, priority)

               try {
                  Files.createFile(path)
               } catch (creationFailure: IOException) {
               }
            }
         }
      }
   }

}
