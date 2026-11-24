package me.collective.client.modules.misc

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ClientChunkCacheReplaceWithPacketDataEvent
import me.collective.client.events.ClientLevelAddEntityEvent
import me.collective.client.events.ClientLevelRemoveEntityEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.managers.TaskScheduler
import me.collective.client.managers.AliasRegistry
import me.collective.client.audio.BuiltinSound
import me.collective.client.managers.SocialRegistry
import me.collective.client.audio.SoundClip
import me.collective.client.managers.SoundManager
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.collections.NamedIdentifier
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.IdentifierSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import com.mojang.authlib.GameProfile
import java.awt.Color
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiConsumer
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MessageSignature
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.Vec3

class VisualRangeModule : Module("VisualRange", "Informs you of players who enter/leave your visual range.", ModuleCategory.MISC) {
   private val joinSetting: Setting<Boolean> = add(BooleanSetting("Join", true))
   private val leaveSetting: Setting<Boolean> = add(BooleanSetting("Leave", true))
   private val targetsSetting: Setting<Boolean> = add(BooleanSetting("Targets", false).markAsGroup())
   private val playersSetting: Setting<Boolean> = add(BooleanSetting("Players", true).dependsOn(this.targetsSetting))
   private val friendsSetting: Setting<Boolean> = add(BooleanSetting("Friends", false).dependsOn(this.targetsSetting))
   private val enemiesSetting: Setting<Boolean> = add(BooleanSetting("Enemies", true).dependsOn(this.targetsSetting))
   private val nakedsSetting: Setting<Boolean> = add(BooleanSetting("Nakeds", false).dependsOn(this.targetsSetting))
   private val signsSetting: Setting<Boolean> = add(BooleanSetting("Signs", false).dependsOn(this.targetsSetting))
   private val enderPearlsSetting: Setting<Boolean> = add(BooleanSetting("EnderPearls", false).dependsOn(this.targetsSetting))
   private val playSoundSetting: Setting<Boolean> = add(BooleanSetting("PlaySound", false).markVisibilityDependency())
   private val onlyEnemiesSetting: Setting<Boolean> = add(BooleanSetting("OnlyEnemies", false).dependsOn(this.playSoundSetting))
   private val soundSetting: Setting<NamedIdentifier> = add(IdentifierSetting("Sound", BuiltinSound.WARNING.identifier).dependsOn(this.playSoundSetting))
   private val volumeSetting: Setting<Float> = add(NumberSetting<Float>("Volume", 1.0f, 0.0f, 1.0f).dependsOn(this.playSoundSetting))

   init {
      this.setDrawn(false)
   }

   private fun playAlertSound() {
      if (this.playSoundSetting.value) {
         val soundManagerSnapshot = ClientContext.soundManager
         val soundId = this.soundSetting.value
         val soundSnapshot: SoundClip = SoundManager.getSound(soundManagerSnapshot, soundId)
         val soundVolume = this.volumeSetting.value
         soundSnapshot.play(soundVolume)
      }
   }

   @EventHandler
   private fun onClientLevelAddEntityEvent(event: ClientLevelAddEntityEvent) {
      if (event.entity is Player) {
         val player = event.entity as Player
         val scheduler: TaskScheduler = ClientContext.taskScheduler
         scheduler.scheduleRotationTask(Runnable {
            if (!this.shouldNotifyPlayer(player)) {
               return@Runnable
            }
            if (!(this.joinSetting.value)) {
               return@Runnable
            }
            val profile: GameProfile = player.gameProfile
            val playerName: String = profile.name()

            val defaultColor = Color.WHITE
            val colorManager: SocialRegistry = ClientContext.socialRegistry
            val playerColor: Color = colorManager.getColor(playerName, defaultColor)
            val nameFormatterSnapshot: AliasRegistry = ClientContext.aliasRegistry
            val message: String = "%s%s entered visual range.".format(
               nameFormatterSnapshot.getPlayerAlias(playerName), ChatFormatting.WHITE
            )
            var notification: MutableComponent = Component.literal(message)
            notification = notification.withStyle { style: Style ->
               val colorHash = playerColor.hashCode()
               style.withColor(colorHash)
            }
            val position = player.position()
            this.addPositionInteractions(notification, position)
            val nameHash = playerName.hashCode()
            var messageId = Math.abs(nameHash) * -1
            messageId /= 2
            val signature: MessageSignature = ChatUtils.createSignature(messageId)
            val priority: ChatUtils.MessagePriority = ChatUtils.MessagePriority.MID
            ChatUtils.printChat(notification, signature, priority)
            val enemyManagerSnapshot: SocialRegistry = ClientContext.socialRegistry

            if (!enemyManagerSnapshot.isEnemyName(playerName) && this.onlyEnemiesSetting.value) {
               return@Runnable
            }

            this.playAlertSound()
         }, 1)
      }
   }

   private fun addPositionInteractions(message: MutableComponent, position: Vec3) {
      val coordinates: String = "%.0f, %.0f, %.0f".format(position.x, position.y, position.z)
      val hoverMessage = "at " + coordinates
      val hoverText: MutableComponent = Component.literal(hoverMessage)
      message.withStyle { style: Style ->
         val hoverEvent: HoverEvent = HoverEvent.ShowText(hoverText)
         style.withHoverEvent(hoverEvent)
      }
      message.withStyle { style: Style ->
         val highlightCoordinates = coordinates.replace(",", "")
         val highlightCommand = "highlight " + highlightCoordinates
         val clickEvent = ChatUtils.suggestCommand(highlightCommand)
         style.withClickEvent(clickEvent)
      }
   }

   @EventHandler
   private fun onClientLevelRemoveEntityEvent(event: ClientLevelRemoveEntityEvent) {
      val levelSnapshot: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return
      val entityId = event.id
      val player = levelSnapshot.getEntity(entityId) as? Player ?: return
      if (!event.isEntityPresent || !this.shouldNotifyPlayer(player)) {
         return
      }
      if (!(this.leaveSetting.value)) {
         return
      }
      val profile: GameProfile = player.gameProfile
      val playerName: String = profile.name()

      val defaultColor = Color.WHITE
      val colorManager: SocialRegistry = ClientContext.socialRegistry
      val playerColor: Color = colorManager.getColor(playerName, defaultColor)
      val nameFormatterSnapshot: AliasRegistry = ClientContext.aliasRegistry
      val message: String = "%s%s left visual range.".format(
         nameFormatterSnapshot.getPlayerAlias(playerName), ChatFormatting.WHITE
      )
      var notification: MutableComponent = Component.literal(message)
      notification = notification.withStyle { style: Style ->
         val colorHash = playerColor.hashCode()
         style.withColor(colorHash)
      }
      val position = player.position()
      this.addPositionInteractions(notification, position)
      val nameHash = playerName.hashCode()
      var messageId = Math.abs(nameHash) * -1
      messageId /= 2
      val signature: MessageSignature = ChatUtils.createSignature(messageId)
      val priority: ChatUtils.MessagePriority = ChatUtils.MessagePriority.LOW
      ChatUtils.printChat(notification, signature, priority)
   }

   @EventHandler
   private fun onClientChunkCacheReplaceWithPacketDataEvent(
      event: ClientChunkCacheReplaceWithPacketDataEvent
   ) {
      if (this.signsSetting.value) {
         val foundSigns = AtomicBoolean(false)
         val chunk: LevelChunk = event.chunk
         val blockEntities: Map<BlockPos, BlockEntity> = chunk.blockEntities
         val notifySign = BiConsumer<BlockPos, BlockEntity> { blockPosition: BlockPos, blockEntity: BlockEntity ->
            if (blockEntity is SignBlockEntity) {
               val sign = blockEntity as SignBlockEntity

               val positionHash = blockPosition.hashCode()
               val signMessageId = -Math.abs(positionHash)

               var notification: MutableComponent = Component.literal("Found a sign: ")
               var empty = true
               val frontTextSnapshot = sign.frontText
               val filtered = false

               for (line in frontTextSnapshot.getMessages(filtered)) {
                  val lineText = line.string
                  val strippedLine = ChatFormatting.stripFormatting(lineText)
                  if (!strippedLine.isNullOrBlank()) {
                     notification.append(line)
                     notification.append(" ")
                     empty = false
                  }
               }

               if (empty) {

                  notification.append("<empty>")
               }

               notification.withStyle { style: Style ->
                  val textColor = ChatFormatting.WHITE
                  style.applyFormat(textColor)
               }
               val signPosition = sign.blockPos
               val position = signPosition.center
               this.addPositionInteractions(notification, position)

               val signature = ChatUtils.createSignature(signMessageId)
               ChatUtils.printChat(notification, signature)
               foundSigns.set(true)
            }
         }
         blockEntities.forEach(notifySign)
         if (foundSigns.get()) {

            this.playAlertSound()
         }
      }
   }

   @EventHandler
   private fun onPacketReceive(
      event: PacketEvent.Receive
   ) {
      if (event.packet !is ClientboundAddEntityPacket) {
         return
      }
      val packet = event.packet as ClientboundAddEntityPacket
      if (packet.type != EntityType.ENDER_PEARL) {
         return
      }
      if (!this.enderPearlsSetting.value) {
         return
      }
      val notification: MutableComponent = Component.literal("Ender Pearl entered visual range.")
      val position = Vec3(packet.x, packet.y, packet.z)
      this.addPositionInteractions(notification, position)
      val signature: MessageSignature = ChatUtils.createSignature(ENDER_PEARL_MESSAGE_ID)
      val priority: ChatUtils.MessagePriority = ChatUtils.MessagePriority.MID
      ChatUtils.printChat(notification, signature, priority)

      this.playAlertSound()
   }

   private fun shouldNotifyPlayer(player: Player): Boolean {
      if (ClientAccess.collectiveMinecraft.player!! === player) {
         return false
      }
      val profile: GameProfile = player.gameProfile
      val playerName: String = profile.name()
      val localPlayer = ClientAccess.collectiveMinecraft.player!!
      if (localPlayer != null && playerName == localPlayer.gameProfile.name()) {
         return false
      }
      val friendManager: SocialRegistry = ClientContext.socialRegistry
      if (friendManager.isFriendPlayer(player)) {
         return this.friendsSetting.value
      }
      val enemyManager: SocialRegistry = ClientContext.socialRegistry
      if (enemyManager.isEnemyPlayer(player)) {
         return this.enemiesSetting.value
      }
      if (!PlayerStateUtils.hasArmor(player)) {
         return this.nakedsSetting.value
      }
      return this.playersSetting.value
   }

   companion object {
      private val ENDER_PEARL_MESSAGE_ID: Int = -Math.abs("EnderPearl".hashCode())
   }
}
