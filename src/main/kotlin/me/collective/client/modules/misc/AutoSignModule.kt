package me.collective.client.modules.misc

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ClientPacketListenerHandleOpenSignEditorEvent
import me.collective.client.events.ScreenChangeEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.utils.network.PacketSender
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.StringSetting

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Queue
import java.util.regex.Matcher
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.screens.inventory.SignEditScreen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket
import net.minecraft.world.level.block.entity.SignBlockEntity

class AutoSignModule : Module("AutoSign", ModuleCategory.MISC) {
   private val fancyDateSetting: Setting<Boolean> = add(BooleanSetting("FancyDate", true))
   private val onlyEmptySetting: Setting<Boolean> = add(BooleanSetting("OnlyEmpty", false))
   private val delaySetting: Setting<Float> = add(NumberSetting<Float>("Delay", 0.0f, 0.0f, 5.0f).withUnit("s"))
   private val line1Setting: Setting<String> = add(StringSetting("Line1", ""))
   private val line2Setting: Setting<String> = add(StringSetting("Line2", ""))
   private val line3Setting: Setting<String> = add(StringSetting("Line3", ""))
   private val line4Setting: Setting<String> = add(StringSetting("Line4", ""))
   private val pendingSigns: Queue<PendingSign> = ArrayDeque()

   private fun hasInputMacro(): Boolean {
      val line1 = this.line1Setting.value
      val line2 = this.line2Setting.value
      val line3 = this.line3Setting.value
      val line4 = this.line4Setting.value
      val combinedLines: String = "\u0001\u0001\u0001\u0001"
         .replaceFirst("\u0001", Matcher.quoteReplacement(line1))
         .replaceFirst("\u0001", Matcher.quoteReplacement(line2))
         .replaceFirst("\u0001", Matcher.quoteReplacement(line3))
         .replaceFirst("\u0001", Matcher.quoteReplacement(line4))
      return combinedLines.contains("<input>")
   }

   private fun getDelayMillis(): Long {
      val delaySeconds = this.delaySetting.value
      val delayMillis = (delaySeconds * 1e+03F).toLong()
      val antiCheatCapture = antiCheat!!
      return if (antiCheatCapture.is2b2tEnabled()) {
         Math.max(400L, delayMillis)
      } else {
         delayMillis
      }
   }

   private fun sendSignUpdate(position: BlockPos, frontText: Boolean) {
      val packet = ServerboundSignUpdatePacket(
         position,
         frontText,
         formatLine(this.line1Setting),
         formatLine(this.line2Setting),
         formatLine(this.line3Setting),
         formatLine(this.line4Setting)
      )
      PacketSender.send(packet)
   }

   private fun formatDate(): String {
      val calendar = Calendar.getInstance()
      val fancyDate = this.fancyDateSetting.value
      return if (fancyDate) {
         val day = calendar.get(5)
         val ordinalDay = formatOrdinalDay(day)
         val fancyFormatterSnapshot = monthYearDateFormat
         val fancyTime = calendar.time
         val monthYear = fancyFormatterSnapshot.format(fancyTime)
         ordinalDay + " " + monthYear
      } else {
         val numericFormatterSnapshot = numericDateFormat
         val numericTime = calendar.time
         numericFormatterSnapshot.format(numericTime)
      }
   }

   private fun isSignEmpty(sign: SignBlockEntity, frontText: Boolean): Boolean {
      val textSnapshot = sign.getText(frontText)
      for (message in textSnapshot.getMessages(false)) {
         val line = message.string
         if (!line.isBlank()) {
            return false
         }
      }
      return true
   }

   @EventHandler
   private fun onScreenChanged(
      event: ScreenChangeEvent.AfterInit
   ) {
      if (event.newScreen is SignEditScreen) {
         val signScreen = event.newScreen as SignEditScreen
         if (this.hasInputMacro()) {
            val onlyEmpty = this.onlyEmptySetting.value
            if (onlyEmpty) {
               for (message in signScreen.messages) {
                  if (!message.isBlank()) {
                     return
                  }
               }
            }
            var messages = signScreen.messages
            messages[0] = this.formatLine(this.line1Setting)
            messages = signScreen.messages
            messages[1] = this.formatLine(this.line2Setting)
            messages = signScreen.messages
            messages[2] = this.formatLine(this.line3Setting)
            messages = signScreen.messages
            messages[3] = this.formatLine(this.line4Setting)
            var inputLineSelected = false
            for (lineIndex in signScreen.messages.indices) {
               val inputCheckLine = signScreen.messages[lineIndex]
               if (inputCheckLine.contains("<input>")) {
                  messages = signScreen.messages
                  val inputReplaceLine = signScreen.messages[lineIndex]
                  messages[lineIndex] = inputReplaceLine.replace("<input>", "")
                  if (!inputLineSelected) {
                     signScreen.line = lineIndex
                     inputLineSelected = true
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private fun onOpenSignEditor(
      event: ClientPacketListenerHandleOpenSignEditorEvent
   ) {
      if (!this.hasInputMacro()) {
         val onlyEmpty = this.onlyEmptySetting.value
         if (onlyEmpty) {
            val signSnapshot = event.sign
            val emptyFrontText = event.frontText
            if (!this.isSignEmpty(signSnapshot, emptyFrontText)) {
               return
            }
         }
         val delaySettingSnapshot = this.delaySetting
         if (delaySettingSnapshot.isAtMinimum) {
            val immediatePositionSnapshot = event.position
            val immediateFrontSnapshot = event.frontText
            this.sendSignUpdate(immediatePositionSnapshot, immediateFrontSnapshot)
         } else {
            synchronized(this.pendingSigns) {
               val queueSnapshot = this.pendingSigns
               queueSnapshot.add(
                  PendingSign(
                     pendingSignPosition(event), event.frontText, pendingSignDueAtMillis()
                  )
               )
            }
         }
         event.cancel()
      }
   }

   private fun pendingSignPosition(event: ClientPacketListenerHandleOpenSignEditorEvent): BlockPos {
      return event.position
   }

   private fun pendingSignDueAtMillis(): Long {
      val queuedAtMillis = System.currentTimeMillis()
      return queuedAtMillis + this.getDelayMillis()
   }

   @EventHandler
   private fun onPlayerTick(event: LocalPlayerPreTickEvent) {
      synchronized(this.pendingSigns) {
         if (!this.pendingSigns.isEmpty()) {
            val dueAtMillis = this.pendingSigns.peek()!!
               .dueAtMillis
            if (dueAtMillis <= System.currentTimeMillis()) {
               val pendingSign = this.pendingSigns.poll()
               if (pendingSign != null) {
                  val positionCapture = pendingSign.position
                  val frontCapture = pendingSign.frontText
                  this.sendSignUpdate(positionCapture, frontCapture)
               }
            }
         }
      }
   }

   private fun formatLine(lineSetting: Setting<String>): String {
      var line = lineSetting.value
      line = line.replace("<date>", this.formatDate())
      val minecraft = ClientAccess.collectiveMinecraft
      val user = minecraft.user
      line = line.replace("<name>", user.name)
      val configuredMaximumLength = 16
      val lineLength = line.length
      val endIndex = Math.min(configuredMaximumLength, lineLength)
      return line.substring(0, endIndex)
   }

   init {
      val formattedDescription = "Puts stuff on your signs automatically\n%sMacros: <date> <name> <input>\n".format(ChatFormatting.YELLOW)
      this.setDescription(formattedDescription)
   }

   private data class PendingSign(
      val position: BlockPos,
      val frontText: Boolean,
      val dueAtMillis: Long
   )

   companion object {
      private var antiCheat: AntiCheatModule? = null
      private val numericDateFormat: DateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
      private val monthYearDateFormat: DateFormat = SimpleDateFormat("MMM yyyy", Locale.ENGLISH)

      private fun formatOrdinalDay(day: Int): String {
         if (day >= 11) {
            if (day <= 13) {

               return day.toString() + "th"
            }
         }
         return when (day % 10) {
            1 -> day.toString() + "st"
            2 -> day.toString() + "nd"
            3 -> day.toString() + "rd"
            else -> day.toString() + "th"
         }
      }

      @JvmStatic
      fun bindModules(manager: ModuleRegistry) {
         antiCheat = manager.getModule(AntiCheatModule::class.java) as AntiCheatModule
      }
   }
}
