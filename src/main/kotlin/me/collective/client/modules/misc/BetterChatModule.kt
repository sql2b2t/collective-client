package me.collective.client.modules.misc


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ClientPacketListenerSendChatEvent
import me.collective.client.events.ChatMessageEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.HudRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.audio.BuiltinSound
import me.collective.client.utils.ClampedInterpolatedFloat
import me.collective.client.audio.SoundClip
import me.collective.client.managers.SoundManager
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.events.EventStage
import me.collective.client.utils.collections.NamedIdentifier
import me.collective.client.settings.options.SocialRole
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.IdentifierSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.StringSetting

import java.awt.Color
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.ArrayList
import java.util.Optional
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.function.UnaryOperator
import java.util.regex.Matcher
import java.util.regex.Pattern
import net.minecraft.client.GuiMessageTag
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import net.minecraft.network.protocol.game.ServerboundChatPacket

class BetterChatModule : Module("BetterChat", "Allows you to customize your chat as you please.", ModuleCategory.MISC) {
   private val timestampsSetting: Setting<Boolean>
   private val onlyServerSetting: Setting<Boolean>
   private val formatSetting: Setting<FormatChoice>
   private val textColorSetting: Setting<Color>
   private val bracketsColorSetting: Setting<Color>
   private val rainbowSetting: Setting<Boolean>
   private val rainbowAmountSetting: Setting<Int>
   private val leftBracketSetting: Setting<String>
   private val rightBracketSetting: Setting<String>
   private val mentionSoundSetting: Setting<Boolean>
   private val mentionSoundModeSetting: Setting<NamedIdentifier>
   private val mentionVolumeSetting: Setting<Float>
   private val privateMessageSoundSetting: Setting<Boolean>
   private val privateMessageSoundModeSetting: Setting<NamedIdentifier>
   private val privateMessageVolumeSetting: Setting<Float>
   private val animationSetting: Setting<Boolean>

   @JvmField
   val typeSetting: Setting<TypeChoice>

   @JvmField
   val speedSetting: Setting<Float>

   @JvmField
   val alphaSetting: Setting<Int>

   @JvmField
   val alwaysSetting: Setting<Boolean>

   private val messageSetting: Setting<Boolean>
   private val prefixSetting: Setting<String>
   private val suffixSetting: Setting<String>
   val highlightSetting: Setting<Boolean>
   val colorSetting: Setting<Color>
   private val selfHighlightSetting: Setting<Boolean>
   private val friendsHighlightSetting: Setting<Boolean>
   private val enemiesHighlightSetting: Setting<Boolean>

   @JvmField
   val longChatHistorySetting: Setting<Boolean>

   @JvmField
   val noResetSetting: Setting<Boolean>

   @JvmField
   val bounceAnimation: ClampedInterpolatedFloat

   private var outgoingMessagePending: Boolean = false

   init {
      val timestamps = BooleanSetting("Timestamps", false)
      timestamps.markVisibilityDependency()
      timestampsSetting = add(timestamps)
      onlyServerSetting = add(BooleanSetting("OnlyServer", true).dependsOn(timestampsSetting))
      val formatVisibility = Predicate<Any> { _ -> timestampsSetting.isExpanded }
      formatSetting = add(EnumSetting("Format", FormatChoice.MINUTES, formatVisibility))
      val textColorVisibility = Predicate<Any> { _ -> timestampsSetting.isExpanded }
      textColorSetting = add(ColorSetting("TextColor", Color(-1, true), textColorVisibility))
      val bracketsColorVisibility = Predicate<Any> { _ -> timestampsSetting.isExpanded }
      bracketsColorSetting = add(ColorSetting("BracketsColor", Color(-8355712, true), bracketsColorVisibility))
      val rainbowVisibility = Predicate<Any> { _ -> timestampsSetting.isExpanded }
      rainbowSetting = add(BooleanSetting("Rainbow", false, rainbowVisibility))
      val rainbowAmountVisibility = Predicate<Any> { _ -> timestampsSetting.isExpanded && rainbowSetting.value }
      rainbowAmountSetting = add(NumberSetting("RainbowAmount", 10, 1, 100, rainbowAmountVisibility))
      val leftBracketVisibility = Predicate<Any> { _ -> timestampsSetting.isExpanded }
      leftBracketSetting = add(StringSetting("LeftBracket", "<", leftBracketVisibility))
      val rightBracketVisibility = Predicate<Any> { _ -> timestampsSetting.isExpanded }
      rightBracketSetting = add(StringSetting("RightBracket", ">", rightBracketVisibility))
      val mentionSound = BooleanSetting("MentionSound", false)
      mentionSound.markVisibilityDependency()
      mentionSoundSetting = add(mentionSound)
      mentionSoundModeSetting = add(IdentifierSetting("SoundMode", BuiltinSound.CSS.identifier).dependsOn(mentionSoundSetting))
      mentionVolumeSetting = add(NumberSetting("Volume", 1.0f, 0.0f, 1.0f).dependsOn(mentionSoundSetting))
      val privateMessageSoundToggle = BooleanSetting("PMSound", false)
      privateMessageSoundToggle.markVisibilityDependency()
      privateMessageSoundSetting = add(privateMessageSoundToggle)
      privateMessageSoundModeSetting = add(IdentifierSetting("SoundMode", BuiltinSound.VK.identifier).dependsOn(privateMessageSoundSetting))
      privateMessageVolumeSetting = add(NumberSetting("Volume", 0.5f, 0.0f, 1.0f).dependsOn(privateMessageSoundSetting))
      val animation = BooleanSetting("Animation", false)
      animation.markVisibilityDependency()
      animationSetting = add(animation)
      val animationTypeVisibility = Predicate<Any> { _ -> animationSetting.isExpanded }
      typeSetting = add(EnumSetting("Type", TypeChoice.HORIZONTAL, animationTypeVisibility))
      val animationSpeedVisibility = Predicate<Any> { _ -> animationSetting.isExpanded }
      speedSetting = add(NumberSetting("Speed", 1.0f, 0.1f, 3.0f, animationSpeedVisibility))
      alphaSetting = add(NumberSetting("Alpha", 250, 0, 1000).withUnit("ms").dependsOn(animationSetting))
      alwaysSetting = add(BooleanSetting("Always", false).dependsOn(animationSetting))
      val message = BooleanSetting("Message", false)
      message.markVisibilityDependency()
      messageSetting = add(message)
      prefixSetting = add(StringSetting("Prefix", "").dependsOn(messageSetting))
      suffixSetting = add(StringSetting("Suffix", "⋆ ᴍɪᴏ").dependsOn(messageSetting))
      val highlight = BooleanSetting("Highlight", false)
      highlight.markVisibilityDependency()
      highlightSetting = add(highlight)
      selfHighlightSetting = add(BooleanSetting("Self", true).dependsOn(highlightSetting))
      friendsHighlightSetting = add(BooleanSetting("Friends", false).dependsOn(highlightSetting))
      enemiesHighlightSetting = add(BooleanSetting("Enemies", false).dependsOn(highlightSetting))
      colorSetting = add(ColorSetting("Color", Color(-43691, true)).dependsOn(highlightSetting, selfHighlightSetting))
      longChatHistorySetting = add(BooleanSetting("LongChatHistory", false))
      noResetSetting = add(BooleanSetting("NoReset", false))
      bounceAnimation = ClampedInterpolatedFloat(Supplier { speedSetting.value }, true)
      alphaSetting.setBoundaryLabel("None", SettingLabelCondition.MIN)
      instance = this
      setDrawn(false)
      colorSetting.setConfigName("HighlightColor")
      privateMessageSoundModeSetting.setConfigName("PMSoundMode")
      privateMessageVolumeSetting.setConfigName("PMVolume")
   }

   private fun restartBounceAnimation() {
      if (isAnimationEnabled()) {
         val animationType = typeSetting
         if (animationType.value == TypeChoice.BOUNCE) {
            bounceAnimation.setValue(true)
            return
         }
      }
   }

   fun highlightNames(text: FormattedText): FormattedText {
      if (!isToggled() || !highlightSetting.value) return text
      val names = ArrayList<NameHighlight>()
      if (selfHighlightSetting.value) {
         names.add(NameHighlight(ClientAccess.collectiveMinecraft.user.name, colorSetting.value))
      }
      val socials = ClientContext.socialRegistry
      if (socials != null) {
         for (role in listOf(SocialRole.FRIEND, SocialRole.ENEMY)) {
            if (!(if (role == SocialRole.FRIEND) friendsHighlightSetting else enemiesHighlightSetting).value) continue
            for (entryObject in socials.getRegistry()) {
               if (entryObject is SocialEntry && entryObject.role() == role) {
                  names.add(
                     NameHighlight(
                        entryObject.getName(),
                        if (role == SocialRole.FRIEND) socials.friendColor else socials.enemyColor
                     )
                  )
               }
            }
         }
      }
      if (names.isEmpty()) return text
      val result: MutableComponent = Component.empty()
      val consumer = FormattedText.StyledContentConsumer<Any> { style, segment ->
         var cursor = 0
         var unhighlightedStart = 0
         while (cursor < segment.length) {
            var longest: NameHighlight? = null
            for (candidate in names) {
               val name = candidate.name
               val end = cursor + name.length
               if (name.isEmpty() || end > segment.length || !segment.regionMatches(cursor, name, 0, name.length)) continue
               if (cursor > 0 && isNameLetter(segment[cursor - 1])) continue
               if (end < segment.length && isNameLetter(segment[end])) continue
               if (longest == null || name.length > longest.name.length) longest = candidate
            }
            if (longest == null) {
               cursor++
               continue
            }
            if (cursor > unhighlightedStart) {
               result.append(Component.literal(segment.substring(unhighlightedStart, cursor)).setStyle(style))
            }
            val end = cursor + longest.name.length
            val color = longest.color
            val highlightedStyle = Style.EMPTY
               .withColor(Color(color.red, color.green, color.blue, 255).rgb)
               .withBold(style.isBold).withItalic(style.isItalic()).withStrikethrough(style.isStrikethrough())
               .withUnderlined(style.isUnderlined()).withObfuscated(style.isObfuscated())
            result.append(Component.literal(segment.substring(cursor, end)).setStyle(highlightedStyle))
            cursor = end
            unhighlightedStart = cursor
         }
         if (unhighlightedStart < segment.length) {
            result.append(Component.literal(segment.substring(unhighlightedStart)).setStyle(style))
         }
         Optional.empty()
      }
      text.visit(consumer, Style.EMPTY)
      return result
   }

   private data class NameHighlight(val name: String, val color: Color)

   fun isAnimationEnabled(): Boolean {
      return isToggled() && animationSetting.value
   }

   private fun playMentionSound(event: ChatMessageEvent) {
      val mentionToggle = mentionSoundSetting
      if (!mentionToggle.value) {
         return
      }
      val mentionMessage = event.message
      val messageText = mentionMessage!!.string
      val lowercaseMessage = messageText.lowercase()
      val privateMessageToggle = privateMessageSoundSetting
      if (privateMessageToggle.value) {
         if (ChatUtils.isIncomingWhisper(lowercaseMessage)) {
            return
         }
      }
      if (outgoingMessagePending) {
         return
      }
      val client: Minecraft = ClientAccess.collectiveMinecraft
      val user = client.user
      val username = user.name
      if (lowercaseMessage.contains(username.lowercase())) {
         val soundManager: SoundManager = ClientContext.soundManager
         val selectedSound = mentionSoundModeSetting
         val soundId = selectedSound.value
         val sound: SoundClip = soundManager.getSound(soundId)
         val selectedVolume = mentionVolumeSetting
         val volume = selectedVolume.value
         sound.play(volume)
      }
   }

   @EventHandler
   private fun onChatAnimationRender(event: HudRenderEvent) {
      val animation = bounceAnimation
      animation.setTarget(0.0f)
      bounceAnimation.updateAndGet()
   }

   @EventHandler
   private fun onConnectionSendPacketEvent(event: PacketEvent.Send) {
      if (event.isCancelled) {
         return
      }
      if (event.packet !is ServerboundChatPacket &&
         event.packet !is ServerboundChatCommandPacket
      ) {
         return
      }
      outgoingMessagePending = true
   }

   private fun playPrivateMessageSound(event: ChatMessageEvent) {
      if (event.isCancelled) {
         return
      }
      val privateMessageToggle = privateMessageSoundSetting
      if (!privateMessageToggle.value || event.message == null) {
         return
      }
      val privateMessage = event.message
      val messageText = privateMessage!!.string
      if (!ChatUtils.isIncomingWhisper(messageText)) {
         return
      }
      val soundClient: Minecraft = ClientAccess.collectiveMinecraft
      val soundTask = Runnable {
         val soundManager: SoundManager = ClientContext.soundManager
         val selectedSound = privateMessageSoundModeSetting
         val soundId = selectedSound.value
         val sound: SoundClip = soundManager.getSound(soundId)
         val selectedVolume = privateMessageVolumeSetting
         val volume = selectedVolume.value
         sound.play(volume)
      }
      soundClient.executeIfPossible(soundTask)
   }

   @EventHandler
   private fun onChatMessage(event: ChatMessageEvent) {
      if (event.isCancelled || event.stage != EventStage.Pre) {
         return
      }
      playPrivateMessageSound(event)
      playMentionSound(event)
      prependTimestamp(event)
      restartBounceAnimation()
      outgoingMessagePending = false
   }

   @EventHandler
   private fun onClientPacketListenerSendChatEvent(event: ClientPacketListenerSendChatEvent) {
      var message = event.message
      val numericPattern = NUMERIC_MESSAGE_PATTERN
      val numericMessage = numericPattern.matcher(message)
      if (numericMessage.matches()) {
         return
      }
      val messageToggle = messageSetting
      if (!messageToggle.value) {
         return
      }
      val selectedPrefix = prefixSetting
      val prefix = selectedPrefix.value
      val selectedSuffix = suffixSetting
      val suffix = selectedSuffix.value
      // Preserve the original formatter's sequential placeholder replacement.
      message = "\u0001\u0001 \u0001"
         .replaceFirst("\u0001", Matcher.quoteReplacement(prefix))
         .replaceFirst("\u0001", Matcher.quoteReplacement(message))
         .replaceFirst("\u0001", Matcher.quoteReplacement(suffix))
      val messageLength = message.length
      if (messageLength >= 256) {
         message = message.substring(0, 256)
      }
      event.message = message
   }

   private fun prependTimestamp(event: ChatMessageEvent) {
      val timestampsToggle = timestampsSetting
      if (!timestampsToggle.value) {
         return
      }
      val tag = event.tag
      if (tag !== GuiMessageTag.system() &&
         tag !== GuiMessageTag.systemSinglePlayer() &&
         tag !== GuiMessageTag.chatNotSecure()
      ) {
         val onlyServerToggle = onlyServerSetting
         if (onlyServerToggle.value) {
            return
         }
      }
      var timestamp: MutableComponent = Component.empty()
      val selectedLeftBracket = leftBracketSetting
      val leftBracket = selectedLeftBracket.value
      val leftBracketText = Component.literal(leftBracket)
      val leftBracketStyle = UnaryOperator<Style> { style ->
         val selectedColor = bracketsColorSetting
         val color = selectedColor.value
         val rgb = color.hashCode()
         style.withColor(rgb)
      }
      val leftBracketStyled = leftBracketText.withStyle(leftBracketStyle)
      timestamp = timestamp.append(leftBracketStyled)
      val selectedFormat = formatSetting
      val format = selectedFormat.value
      val now = Date()
      val formattedTime = format.format(now)
      val formattedTimestampText = Component.literal(formattedTime)
      val timestampStyle = UnaryOperator<Style> { style ->
         val selectedColor = textColorSetting
         val color = selectedColor.value
         val rgb = color.hashCode()
         style.withColor(rgb)
      }
      val styledTimestampText = formattedTimestampText.withStyle(timestampStyle)
      timestamp = timestamp.append(styledTimestampText)
      val selectedRightBracket = rightBracketSetting
      val rightBracket = selectedRightBracket.value
      val rightBracketText = Component.literal(rightBracket)
      val rightBracketStyle = UnaryOperator<Style> { style ->
         val selectedColor = bracketsColorSetting
         val color = selectedColor.value
         val rgb = color.hashCode()
         style.withColor(rgb)
      }
      val rightBracketStyled = rightBracketText.withStyle(rightBracketStyle)
      timestamp = timestamp.append(rightBracketStyled)
      var prefix: MutableComponent = timestamp.append(" ")
      val rainbowToggle = rainbowSetting
      if (rainbowToggle.value) {
         val rainbowPrefix: MutableComponent = Component.empty()
         val timestampContent = prefix
         val timestampText = timestampContent.string
         val rainbowAmount = Supplier<Int> {
            val selectedAmount = rainbowAmountSetting
            val amount = selectedAmount.value
            val multiplier = 10
            amount * multiplier
         }
         val selectedColor = textColorSetting
         val rainbowColor = selectedColor.value
         val rainbowTimestamp = ChatUtils.rainbowText(timestampText, rainbowAmount, rainbowColor)
         prefix = rainbowPrefix.append(rainbowTimestamp)
      }
      val message = event.message
      val timestampedMessage = prefix.append(message!!)
      event.message = timestampedMessage
   }

   enum class FormatChoice(private val dateFormat: DateFormat, private val displayName: String) : NamedEntry {
      HOURS(SimpleDateFormat("HH"), "Hours"),
      MINUTES(SimpleDateFormat("HH:mm"), "Minutes"),
      SECONDS(SimpleDateFormat("HH:mm:ss"), "Seconds");

      override fun getName(): String {
         return displayName
      }

      fun format(date: Date): String {
         return dateFormat.format(date)
      }
   }

   enum class TypeChoice(private val displayName: String) : NamedEntry {
      VERTICAL("Vertical"),
      HORIZONTAL("Horizontal"),
      BOTH("Both"),
      BOUNCE("Bounce"),
      NONE("None");

      override fun getName(): String {
         return displayName
      }
   }

   companion object {
      private val NUMERIC_MESSAGE_PATTERN: Pattern = Pattern.compile("^\\d+$")

      @JvmField
      var addingMessageToDisplayQueue: Boolean = false

      @JvmField
      var clearingChatFromDebugKey: Boolean = false

      private var instance: BetterChatModule? = null

      @JvmStatic
      fun getInstance(): BetterChatModule? {
         return instance
      }

      private fun isNameLetter(character: Char): Boolean {
         return character in 'a'..'z' || character in 'A'..'Z'
      }

      @JvmStatic
      fun bindReferencedModules(registry: ModuleRegistry) {
         instance = registry.getModule(
            BetterChatModule::class.java
         ) as BetterChatModule
      }
   }
}
