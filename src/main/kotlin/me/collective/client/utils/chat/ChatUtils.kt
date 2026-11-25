package me.collective.client.utils.chat

import com.mojang.logging.LogUtils
import java.awt.Color
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Supplier
import java.util.function.UnaryOperator
import java.util.regex.Pattern
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.CommandRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.client.NotificationsModule
import me.collective.client.render.ColorUtils
import me.collective.client.render.DynamicColorStyle
import net.minecraft.ChatFormatting
import net.minecraft.client.GuiMessageTag
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MessageSignature
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import org.slf4j.Logger

class ChatUtils private constructor() : ClientAccess {
   enum class MessagePriority {
      HIGH { override fun getColor(): Color { return Color(225, 10, 10) } },
      MID { override fun getColor(): Color { return Color(225, 170, 10) } },
      LOW { override fun getColor(): Color { return Color(225, 225, 225) } };

      abstract fun getColor(): Color
   }

   companion object {
      private val LOGGER: Logger = LogUtils.getLogger()

      @JvmField
      val DEFAULT_SIGNATURE: MessageSignature = createSignature(-1)

      private val NOTIFICATIONS: NotificationsModule get() = ClientContext.getModule(NotificationsModule::class.java)

      @JvmField
      val MESSAGE_TAG: GuiMessageTag = GuiMessageTag(-1, null, Component.empty(), "Collective")

      private val ARROW_WHISPER_PATTERN: Pattern = Pattern.compile("""^(\w{3,16}) -> (\w{3,16}): (.*)""")

      @JvmField
      val INCOMING_WHISPER_PATTERNS: List<Pattern> = listOf(
         Pattern.compile("""^From (\w{3,16}): (.*)"""),
         Pattern.compile("""^from (\w{3,16}): (.*)"""),
         Pattern.compile("""^(\w{3,16}) whispers: (.*)"""),
         Pattern.compile("""^(\w{3,16}) -> me (.*)"""),
         Pattern.compile("""^(\w{3,16}) whispers to you: (.*)"""),
         Pattern.compile("""^(\w{3,16}) says: (.*)"""),
         Pattern.compile("""^(\w{3,16}) пишет: (.*)"""),
         ARROW_WHISPER_PATTERN
      )

      @JvmField
      val OUTGOING_WHISPER_PATTERNS: List<Pattern> = listOf(
         Pattern.compile("""^To (\w{3,16}): (.*)"""),
         Pattern.compile("""^to (\w{3,16}): (.*)"""),
         Pattern.compile("""^\[me -> (\w{3,16})\] (.*)"""),
         Pattern.compile("""^You whisper to (\w{3,16}): (.*)"""),
         Pattern.compile("""^К (\w{3,16}): (.*)"""),
         ARROW_WHISPER_PATTERN
      )

      @JvmStatic
      fun isIncomingWhisper(message: String): Boolean {
         val username = ClientAccess.collectiveMinecraft.user.name.lowercase(Locale.ROOT)
         if (message.lowercase(Locale.ROOT).startsWith(username)) return false
         if (message.contains(" шепчет: ") || message.contains(" шепчет тебе: ")) return true
         for (pattern in INCOMING_WHISPER_PATTERNS) {
            if (pattern.matcher(message).find()) return true
         }
         return false
      }

      @JvmStatic
      fun suggestCommand(command: String): ClickEvent {
         return ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, CommandRegistry.getPrefix() + command)
      }

      @JvmStatic
      fun createPrefix(): Component {
         return Component.empty()
            .append(Component.literal("[").withStyle(UnaryOperator<Style> { style -> withDynamicColor(style) { NOTIFICATIONS.bracketColor } }))
            .append(Component.literal("Collective").withStyle(UnaryOperator<Style> { style -> withDynamicColor(style) { NOTIFICATIONS.watermarkColor } }))
            .append(Component.literal("]").withStyle(UnaryOperator<Style> { style -> withDynamicColor(style) { NOTIFICATIONS.bracketColor } }))
      }

      @JvmStatic
      fun bracketedText(text: String): Component {
         return Component.empty().append(Component.literal("[").withStyle(ChatFormatting.GRAY))
            .append(text).append(Component.literal("]").withStyle(ChatFormatting.GRAY))
      }

      @JvmStatic
      fun objectSignature(value: Any): MessageSignature {
         return createSignature(-Math.abs(value.hashCode()))
      }

      @JvmStatic
      fun sendChatOrCommand(message: String) {
         val minecraft = ClientAccess.collectiveMinecraft
         minecraft.execute(Runnable {
            val connection: ClientPacketListener = minecraft.player?.connection ?: return@Runnable
            if (message.startsWith("/")) connection.sendCommand(message.substring(1))
            else connection.sendChat(message)
         })
      }

      @JvmStatic
      fun isOutgoingWhisper(message: String): Boolean {
         val username = ClientAccess.collectiveMinecraft.user.name.lowercase(Locale.ROOT)
         val startsWithUsername = message.lowercase(Locale.ROOT).startsWith(username)
         for (pattern in OUTGOING_WHISPER_PATTERNS) {
            if ((pattern !== ARROW_WHISPER_PATTERN || startsWithUsername) && pattern.matcher(message).find()) return true
         }
         return false
      }

      @JvmStatic
      fun withDynamicColor(style: Style, colorSupplier: Supplier<Int>): Style {
         return (style.withColor(ThreadLocalRandom.current().nextInt()) as Any as DynamicColorStyle)
            .`collective$withColor`(colorSupplier)
      }

      @JvmStatic
      fun rainbowText(text: String, offsetSupplier: Supplier<Int>, baseColor: Color): Component {
         val result: MutableComponent = Component.empty()
         val hsb = Color.RGBtoHSB(baseColor.red, baseColor.green, baseColor.blue, null)
         for (index in text.indices) {
            val characterIndex = index
            result.append(Component.literal(text[index].toString()).withStyle(
               UnaryOperator<Style> { style ->
                  withDynamicColor(style) {
                     ColorUtils.rainbow(characterIndex * offsetSupplier.get(), hsb[1], hsb[2], 255).rgb
                  }
               }
            ))
         }
         return result
      }

      @JvmStatic
      fun printChat(message: Component, signature: MessageSignature?, priority: MessagePriority) {
         val decorated: MutableComponent = Component.empty()
            .append(Component.literal("[").withStyle(UnaryOperator<Style> { style -> style.withColor(priority.getColor().darker().hashCode()) }))
            .append(Component.literal("!").withStyle(UnaryOperator<Style> { style -> style.withColor(priority.getColor().hashCode()) }))
            .append(Component.literal("] ").withStyle(UnaryOperator<Style> { style -> style.withColor(priority.getColor().darker().hashCode()) }))
         printChat(decorated.append(message), signature)
      }

      @JvmStatic
      fun printChat(message: Component, signature: MessageSignature?) {
         ClientAccess.collectiveMinecraft.execute {
            try {
               ClientAccess.collectiveMinecraft.gui.chat.addMessage(
                  Component.empty().append(createPrefix()).append(" ").append(message), signature, MESSAGE_TAG)
            } catch (failure: RuntimeException) {
               LOGGER.warn("Could not display chat message", failure)
            }
         }
      }

      @JvmStatic
      fun moduleSignature(module: Module): MessageSignature {
         return createSignature(-Math.abs(module.name.hashCode()))
      }

      @JvmStatic
      fun printChat(message: Component, id: Int) {
         printChat(message, createSignature(id))
      }

      @JvmStatic
      fun watermarkText(text: String?): Component {
         return if (text == null) Component.empty() else Component.literal(text).withStyle(UnaryOperator<Style> { style ->
            withDynamicColor(style) { NOTIFICATIONS.watermarkSetting.value.hashCode() }
         })
      }

      @JvmStatic
      fun createSignature(id: Int): MessageSignature {
         return MessageSignature(ByteBuffer.allocate(256).putInt(id).array())
      }
   }
}
