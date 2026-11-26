package me.collective.client.modules.misc

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ChatMessageEvent
import me.collective.client.events.MinecraftTickEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.managers.WebhookManager
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.events.EventStage
import me.collective.client.managers.CommandRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import java.awt.Color
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.concurrent.TimeUnit
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSink

class WebhookNotifsModule : Module("WebhookNotifs", "Sends chat messages to your webhook.", ModuleCategory.MISC, "DiscordNotifs") {
   private val timeoutSetting: Setting<Float>
   private val colorSetting: Setting<Boolean>
   private val timestampSetting: Setting<Boolean>
   private val ignoreCollectiveSetting: Setting<Boolean>
   private val queueSetting: Setting<Boolean>
   private val disconnectSetting: Setting<Boolean>
   private val dMSetting: Setting<Boolean>
   private val chatMessagesSetting: Setting<Boolean>
   private val visualRangeSetting: Setting<Boolean>
   private val stashSetting: Setting<Boolean>
   private val pingSetting: Setting<Boolean>
   private val timestampFormatter: DateTimeFormatter
   private val pendingMessages: MutableList<String>
   private val includedMessages: MutableList<String>
   private var pingPending: Boolean = false
   private val notificationTimer: ElapsedTimer
   private var previousQueueMessage: String? = null
   private var lastServer: ServerData? = null

   init {
      this.timeoutSetting = this.add(NumberSetting<Float>("Timeout", 1.0f, 1.0f, 10.0f).withUnit("s"))
      this.colorSetting = this.add(BooleanSetting("Color", false))
      this.timestampSetting = this.add(BooleanSetting("Timestamp", false))
      this.ignoreCollectiveSetting = this.add(BooleanSetting("IgnoreCollective", true))
      val types = BooleanSetting("Types", false)
      types.markAsGroup()
      types.markVisibilityDependency()
      val registeredTypes = this.add(types)
      this.queueSetting = this.add(BooleanSetting("Queue", true).dependsOn(registeredTypes))
      this.disconnectSetting = this.add(BooleanSetting("Disconnect", true).dependsOn(registeredTypes))
      this.dMSetting = this.add(BooleanSetting("DM", true).dependsOn(registeredTypes))
      this.chatMessagesSetting = this.add(BooleanSetting("ChatMessages", false).dependsOn(registeredTypes))
      this.visualRangeSetting = this.add(BooleanSetting("VisualRange", false).dependsOn(registeredTypes))
      val stash = BooleanSetting("Stash", false)
      stash.markVisibilityDependency()
      val registeredStash = this.add(stash.dependsOn(registeredTypes))
      this.stashSetting = registeredStash
      this.pingSetting = this.add(BooleanSetting("Ping", false).dependsOn(registeredTypes, registeredStash))
      this.timestampFormatter = DateTimeFormatter.ofPattern("HH:mm")
      this.pendingMessages = Collections.synchronizedList(ArrayList<String>())
      this.includedMessages = ArrayList()
      this.notificationTimer = ElapsedTimer()
   }

   override fun onEnable() {
      this.notificationTimer.reset()
      if (ClientContext.webhookManager.isConfigured()) return
      val prefix = CommandRegistry.getPrefix()
      ChatUtils.printChat(Component.literal("Set a notification destination with " + prefix + "webhook set <url> or " +
         prefix + "webhook telegram <bot-token> <chat-id>."), ChatUtils.createSignature(-10395), ChatUtils.MessagePriority.MID)
   }

   private fun formatMessage(event: ChatMessageEvent): String {
      if (!this.colorSetting.value) return event.originalText!!
      val message = StringBuilder()
      val previousColor = intArrayOf(-1)
      event.originalMessage!!.visualOrderText.accept(FormattedCharSink { index, style, codePoint ->
         val color = style.color?.value ?: -1
         if (color != previousColor[0]) message.append("\u001b[").append(getClosestAnsiColor(color)).append("m")
         message.appendCodePoint(codePoint)
         previousColor[0] = color
         true
      })
      return message.toString()
   }

   @EventHandler
   private fun onMinecraftDisconnectEvent(event: MinecraftDisconnectEvent) {
      val connectedServer = this.lastServer
      if (this.disconnectSetting.value && connectedServer != null) {
         this.enqueueLines(("You have been disconnected from " + connectedServer.ip + "!").split("\n").toTypedArray())
         this.lastServer = null
      }
   }

   private fun enqueueLines(lines: Array<String>) {
      val message = StringBuilder()
      for (line in lines) {
         if (!line.isEmpty()) message.append(ChatFormatting.stripFormatting(line)).append('\n')
      }
      if (message.length >= 2) this.pendingMessages.add(message.substring(0, message.length - 1))
   }

   @EventHandler
   private fun onChatMessage(event: ChatMessageEvent) {
      if (event.isCancelled) {
         return
      }
      if (!ClientContext.webhookManager.isConfigured() || event.stage != EventStage.Pre || event.originalText == null) return
      if (this.ignoreCollectiveSetting.value && event.tag === ChatUtils.MESSAGE_TAG) return

      val message = this.formatMessage(event)
      val lines = message.split("\n").toTypedArray()

      val incomingDirectMessage = ChatUtils.isIncomingWhisper(message)

      val outgoingDirectMessage = ChatUtils.isOutgoingWhisper(message)
      val stashMessage = this.isStashMessage(event)
      val visualRangeMessage = this.isVisualRangeMessage(event)
      if (incomingDirectMessage || outgoingDirectMessage) {
         val notifyDirectMessages = this.dMSetting.value
         if (notifyDirectMessages) {
            this.enqueueLines(lines)
         }
         return
      }

      if (message.contains("Position in queue:")) {
         val lineCount = lines.size

         if (lineCount > 15) {
            if (this.previousQueueMessage == null) {
               this.previousQueueMessage = message
            } else if (this.previousQueueMessage != message) {
               val notifyQueue = this.queueSetting.value
               if (notifyQueue) {
                  this.enqueueLines(lines)
                  this.previousQueueMessage = message
               }

               return
            }

            return
         }
      }

      val notifyChatMessages = this.chatMessagesSetting.value
      if (notifyChatMessages || stashMessage || visualRangeMessage) {
         if (stashMessage) {
            val pingEveryone = this.pingSetting.value
            if (pingEveryone) {
               this.pingPending = true
            }
         }

         this.enqueueLines(lines)
      }
   }

   private fun isVisualRangeMessage(event: ChatMessageEvent): Boolean {
      return this.visualRangeSetting.value && event.signature != null &&
         event.signature!!.asByteBuffer().int <= 0 && event.message!!.string.contains("visual range")
   }

   private fun isStashMessage(event: ChatMessageEvent): Boolean {
      return this.stashSetting.value && event.signature != null &&
         event.signature!!.asByteBuffer().int == -13579
   }

   @EventHandler
   private fun onClientUpdate(event: MinecraftTickEvent) {
      if (ClientAccess.collectiveMinecraft.level != null) this.lastServer = ClientContext.serverStateManager.getServerData()
      val manager: WebhookManager = ClientContext.webhookManager
      if (!manager.isConfigured() || !this.notificationTimer.hasElapsed(this.timeoutSetting.value.toDouble(), TimeUnit.SECONDS) ||
         this.pendingMessages.isEmpty()) return
      this.notificationTimer.reset()
      synchronized(this.pendingMessages) {
         if (this.pendingMessages.isEmpty()) return
         val mention = if (this.pingPending && !manager.isTelegram()) "@everyone\n" else ""
         this.pingPending = false
         val batch = StringBuilder()
         val timestamp = "[" + this.timestampFormatter.format(LocalDateTime.now()) + "] "
         for (message in this.pendingMessages) {
            if (batch.length > 1900) break
            if (this.timestampSetting.value) batch.append(timestamp)
            batch.append(message).append('\n')
            this.includedMessages.add(message)
         }
         val text = batch.substring(0, batch.length - 1).replace("`", "'")
         if (manager.isTelegram()) {
            manager.sendContent(text)
         } else {
            val language = if (this.colorSetting.value) "ansi\n" else ""
            manager.sendContent(mention + "```" + language + text + "```")
         }
         for (message in this.includedMessages) this.pendingMessages.remove(message)
         this.includedMessages.clear()
      }
   }

   companion object {

      private val ANSI_PALETTE: Map<Color, String> = HashMap<Color, String>().apply {
         put(Color(0, 0, 0), "30")
         put(Color(255, 0, 0), "31")
         put(Color(0, 255, 0), "32")
         put(Color(255, 170, 0), "33")
         put(Color(0, 0, 255), "34")
         put(Color(255, 0, 255), "35")
         put(Color(0, 255, 255), "36")
         put(Color(170, 170, 170), "37")
      }

      @JvmStatic
      fun getClosestAnsiColor(rgb: Int): String {
         var closestCode = "37"
         var closestDistance = Double.MAX_VALUE
         for ((color, paletteCode) in ANSI_PALETTE) {
            val red = (color.red - ARGB.red(rgb)).toDouble()
            val green = (color.green - ARGB.green(rgb)).toDouble()
            val blue = (color.blue - ARGB.blue(rgb)).toDouble()
            val distance = Math.pow(red, 2.0) + Math.pow(green, 2.0) + Math.pow(blue, 2.0)
            if (distance < closestDistance) {
               closestDistance = distance
               closestCode = paletteCode
            }
         }
         return closestCode
      }
   }
}
