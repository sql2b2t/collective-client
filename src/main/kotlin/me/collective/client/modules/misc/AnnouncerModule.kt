package me.collective.client.modules.misc

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ItemUseEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MultiPlayerGameModeDestroyBlockEvent
import me.collective.client.utils.movement.MovementSpeedTracker
import me.collective.client.utils.ElapsedTimer
import me.collective.client.managers.SocialRegistry
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.SocialRole
import me.collective.client.utils.math.MathUtils

import java.awt.Color
import java.text.DecimalFormat
import java.util.Random
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MessageSignature
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry
import net.minecraft.network.protocol.game.ServerboundChatPacket

class AnnouncerModule : Module("Announcer", "Lets the others know what you're doing.", ModuleCategory.MISC) {
   private val clientSideSetting: Setting<Boolean>
   private val compactSetting: Setting<Boolean>
   private val joinsSetting: Setting<Boolean>
   private val friendsSetting: Setting<Boolean>
   private val enemiesSetting: Setting<Boolean>
   private val othersSetting: Setting<Boolean>
   private val moveSetting: Setting<Boolean>
   private val breakSetting: Setting<Boolean>
   private val eatSetting: Setting<Boolean>
   private val delaySetting: Setting<Double>
   private val announcementTimer: ElapsedTimer
   private var distanceTravelled: Double = 0.0
   private var foodConsumed: Int = 0
   private var blocksBroken: Int = 0

   init {
      val clientSideOption = BooleanSetting("ClientSide", false)
      clientSideOption.markVisibilityDependency()
      clientSideSetting = add(clientSideOption)
      compactSetting = add(BooleanSetting("Compact", false).dependsOn(clientSideSetting))
      val joinsOption = BooleanSetting("Joins", false)
      joinsOption.markVisibilityDependency()
      joinsSetting = add(joinsOption)
      friendsSetting = add(BooleanSetting("Friends", true).dependsOn(joinsSetting))
      enemiesSetting = add(BooleanSetting("Enemies", true).dependsOn(joinsSetting))
      othersSetting = add(BooleanSetting("Others", true).dependsOn(joinsSetting))
      moveSetting = add(BooleanSetting("Move", true))
      breakSetting = add(BooleanSetting("Break", true))
      eatSetting = add(BooleanSetting("Eat", true))
      delaySetting = add(NumberSetting("Delay", 10.0, 0.0, 30.0).withUnit("s"))
      announcementTimer = ElapsedTimer()

      delaySetting.setBoundaryLabel("None", SettingLabelCondition.MIN)

      setDrawn(false)
   }

   private fun chooseMovementMessage(): String {
      val messages = arrayOf(
         "I just flew over {blocks} blocks thanks to https://github.com/spectrumdevs/collective!",
         "Я только что пролетел над {blocks} блоками с помощью https://github.com/spectrumdevs/collective!",
         "https://github.com/spectrumdevs/collective sayesinde {blocks} blok uçtum!",
         "我刚刚用 https://github.com/spectrumdevs/collective 走了 {blocks} 米!",
         "Dank https://github.com/spectrumdevs/collective bin ich gerade über {blocks} Blöcke geflogen!",
         "Jag hoppade precis över {blocks} blocks tack vare https://github.com/spectrumdevs/collective!",
         "Właśnie przeleciałem ponad {blocks} bloki dzięki https://github.com/spectrumdevs/collective!",
         "Es tikko nolidoju {blocks} blokus, paldies https://github.com/spectrumdevs/collective!",
         "Я щойно пролетів понад {blocks} блоками завдяки https://github.com/spectrumdevs/collective!",
         "I just fwew ovew {blocks} bwoccs thanks to https://github.com/spectrumdevs/collective! :3",
         "Ho appena camminato per {blocks} blocchi grazie a https://github.com/spectrumdevs/collective!",
         "עכשיו עפתי {blocks} הודות ל https://github.com/spectrumdevs/collective!",
         "Právě jsem proletěl {blocks} bloků díky https://github.com/spectrumdevs/collective!",
         "Lensin juuri yli {blocks} blokkia https://github.com/spectrumdevs/collective:n ansiosta!",
         "Ravnokar sem preletel {blocks} blokov v zahvali https://github.com/spectrumdevs/collective!",
         "أنا هلق طاير فوق {blocks} بلوكس بفضل https://github.com/spectrumdevs/collective!"
      )
      return messages[ThreadLocalRandom.current().nextInt(messages.size)]
   }

   override fun onEnable() {
      foodConsumed = 0
      blocksBroken = 0
      announcementTimer.reset()
   }

   private fun announce(message: String) {
      val signature: MessageSignature = ChatUtils.moduleSignature(this)
      scheduleAnnouncement(message, signature)
   }

   private fun chooseEatingMessage(): String {
      val messages = arrayOf(
         "I just ate {amount} {name} thanks to https://github.com/spectrumdevs/collective!",
         "Я только что съел {amount} {name} с помощью https://github.com/spectrumdevs/collective!",
         "Tam olarak {amount} tane {name} yedim. TeŞekkürler https://github.com/spectrumdevs/collective",
         "我刚用 https://github.com/spectrumdevs/collective 吃了 {amount} 个 {name}!",
         "Ich habe gerade {amount} {name} dank https://github.com/spectrumdevs/collective gegessen!",
         "Jag åt precis {amount} {name} tack vare https://github.com/spectrumdevs/collective",
         "Właśnie zjadłem {amount} {name} dzięki https://github.com/spectrumdevs/collective",
         "Es tikko apēdu {amount} {name} ar https://github.com/spectrumdevs/collective spēku!",
         "Я щойно з’їв {amount} {name} завдяки https://github.com/spectrumdevs/collective!",
         "I just ate {amount} {name} thanks to https://github.com/spectrumdevs/collective! ^-^",
         "Ho appena mangiato {amount} {name} grazie a https://github.com/spectrumdevs/collective!",
         "כרגע אכלתי {amount} {name} הודות לhttps://github.com/spectrumdevs/collective!",
         "Právě jsem snědl {amount} {name} díky https://github.com/spectrumdevs/collective",
         "Söin juuri {amount} {name}a https://github.com/spectrumdevs/collective:n ansiosta!",
         "Ravnokar sem pojedel {amount} {name} v zahvali https://github.com/spectrumdevs/collective",
         "أنا هلق أكلت {amount} {name} بفضل https://github.com/spectrumdevs/collective!"
      )
      return messages[ThreadLocalRandom.current().nextInt(messages.size)]
   }

   private fun chooseBreakingMessage(): String {
      val messages = arrayOf(
         "I just destroyed {amount} {name} with the power of https://github.com/spectrumdevs/collective!",
         "Я только что разрушил {amount} {name} с помощью https://github.com/spectrumdevs/collective!",
         "Az önce {amount} tane {name} kırdım. TeŞekkürler https://github.com/spectrumdevs/collective!",
         "我刚刚用 https://github.com/spectrumdevs/collective 破坏了 {amount} {name}!",
         "Ich habe gerade {amount} {name} mit der Kraft von https://github.com/spectrumdevs/collective zerstört!",
         "Jag förstörde precis {amount} {name} tack vare https://github.com/spectrumdevs/collective!",
         "Właśnie zniszczyłem {amount} {name} za pomocą https://github.com/spectrumdevs/collective",
         "Es tikko salauzu {amount} {name} ar spēku https://github.com/spectrumdevs/collective!",
         "Я щойно знищив {amount} {name} за допомогою https://github.com/spectrumdevs/collective!",
         "I just destwoyed {amount} {name} with the powew of https://github.com/spectrumdevs/collective! :3",
         "Ho appena distrutto {amount} {name} grazie al potere di https://github.com/spectrumdevs/collective!",
         "בדיוק חצבתי {amount} {name} בעזרת הכוח של https://github.com/spectrumdevs/collective!",
         "Právě jsem zničil {amount} {name} díky https://github.com/spectrumdevs/collective!",
         "Rikoin juuri {amount} {name}ia https://github.com/spectrumdevs/collective:n ansiosta!",
         "Ravnokar sem uničil {amount} {name} z močjo https://github.com/spectrumdevs/collective",
         "أنا هلق دمّرت {amount} {name} بقوة https://github.com/spectrumdevs/collective!"
      )
      return messages[ThreadLocalRandom.current().nextInt(messages.size)]
   }

   @EventHandler
   private fun announceMovement(event: LocalPlayerTickEvent) {
      if (!isWorldUnavailable()) {
         val previousDistance = distanceTravelled
         val movementTracker: MovementSpeedTracker = ClientContext.movementSpeedTracker
         val speed = movementTracker.currentSpeed

         val distancePerSecond = speed / 3.6

         distanceTravelled = previousDistance +
         distancePerSecond / 20.0
         var distanceSnapshot = distanceTravelled

         if (distanceSnapshot >= 1000.0) {
            distanceTravelled = 0.0
         }

         val moveSettingSnapshot = moveSetting
         val moveValue = moveSettingSnapshot.value
         if (moveValue) {
            distanceSnapshot = distanceTravelled

            if (distanceSnapshot >= 1.0) {
               val delayTimer = announcementTimer

               val millisPerSecond = 1000.0
               val delaySettingSnapshot = delaySetting
               val delayValue = delaySettingSnapshot.value
               val delayMillis = (millisPerSecond * delayValue).toLong()
               if (delayTimer.hasElapsed(delayMillis)) {
                  val template = chooseMovementMessage()

                  val distanceFormat = DecimalFormat("0.00")
                  val announcedDistance = distanceTravelled
                  val message = template.replace("{blocks}", distanceFormat.format(announcedDistance))

                  announce(message)
                  distanceTravelled = 0.0
                  announcementTimer.reset()
               }
            }
         }
      }
   }

   private fun scheduleAnnouncement(message: String, signature: MessageSignature) {
      ClientAccess.collectiveMinecraft.executeIfPossible {
         val clientSideSettingSnapshot = clientSideSetting
         val clientSideValue = clientSideSettingSnapshot.value
         if (clientSideValue) {
            ChatUtils.printChat(
               Component.literal(message).withStyle { style ->
                  val color = Color(
                     161,
                     161,
                     161
                  )
                  val colorValue = color.hashCode()
                  style.withColor(colorValue)
               },
               signature
            )
         } else {
            val connection = ClientAccess.collectiveMinecraft.player!!.connection
            connection.sendChat(message)
         }
      }
   }

   @EventHandler
   private fun announceEating(event: ItemUseEvent.BeforeFinish) {
      if (!isWorldUnavailable()) {
         val announceCount = MathUtils.randomInt(1, 6)
         val eatSettingSnapshot = eatSetting
         val eatValue = eatSettingSnapshot.value
         if (eatValue) {
            if (event.stack.has(DataComponents.FOOD)) {
               foodConsumed++
               if (foodConsumed >= announceCount) {
                  var timer = announcementTimer

                  val millisPerSecond = 1000.0
                  val delaySettingSnapshot = delaySetting
                  val delayValue = delaySettingSnapshot.value
                  val delayMillis = (millisPerSecond * delayValue).toLong()
                  if (timer.hasElapsed(delayMillis)) {
                     val template = chooseEatingMessage()

                     val announcedCount = foodConsumed
                     val countedMessage = template.replace("{amount}", announcedCount.toString())
                     val finishedStack = event.stack
                     val food = finishedStack.item
                     val message = countedMessage.replace("{name}", food.name.string)

                     announce(message)
                     foodConsumed = 0
                     timer = announcementTimer

                     timer.reset()
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private fun announceBlockBreak(event: MultiPlayerGameModeDestroyBlockEvent) {
      if (!isWorldUnavailable()) {
         val announceCount = MathUtils.randomInt(1, 6)
         blocksBroken++
         val breakSettingSnapshot = breakSetting
         val breakValue = breakSettingSnapshot.value
         if (breakValue && blocksBroken >= announceCount) {
            var timer = announcementTimer

            val millisPerSecond = 1000.0
            val delaySettingSnapshot = delaySetting
            val delayValue = delaySettingSnapshot.value
            val delayMillis = (millisPerSecond * delayValue).toLong()
            if (timer.hasElapsed(delayMillis)) {
               val blockNames = arrayOfNulls<String>(4)
               val position: BlockPos = event.pos

               val block = BlockInteractionUtils.getBlock(
                  position
               )
               blockNames[0] = block.name.string

               blockNames[1] = "Air"

               blockNames[2] = "Bedrock"

               blockNames[3] = "Barrier"

               val template = chooseBreakingMessage()

               val announcedCount = blocksBroken
               val countedMessage = template.replace(
                  "{amount}",
                  announcedCount.toString()
               )

               val random = Random()
               val blockNameCount = blockNames.size
               val message = countedMessage.replace("{name}", blockNames[random.nextInt(blockNameCount)]!!)

               announce(message)
               blocksBroken = 0
               timer = announcementTimer

               timer.reset()
            }
         }
      }
   }

   @EventHandler
   private fun resetDelayAfterChat(event: PacketEvent.Send) {
      if (event.packet is ServerboundChatPacket) {
         announcementTimer.reset()
      }
   }

   private fun shouldAnnounceRelationship(relationship: SocialRole?): Boolean {
      val friendsSettingSnapshot = friendsSetting
      val friendsValue = friendsSettingSnapshot.value
      if (!friendsValue && relationship == SocialRole.FRIEND) {
         return false
      }

      val enemiesSettingSnapshot = enemiesSetting
      val enemiesValue = enemiesSettingSnapshot.value
      if (!enemiesValue && relationship == SocialRole.ENEMY) {
         return false
      }

      val othersSettingSnapshot = othersSetting
      val othersValue = othersSettingSnapshot.value
      return othersValue || relationship != null
   }

   @EventHandler
   private fun announcePlayerListChanges(event: PacketEvent.Receive) {
      val joinsSettingSnapshot = joinsSetting
      val joinsValue = joinsSettingSnapshot.value
      if (joinsValue) {
         val timer = announcementTimer
         val delaySettingSnapshot = delaySetting
         val delaySeconds = delaySettingSnapshot.value
         val seconds = TimeUnit.SECONDS
         if (timer.hasElapsed(delaySeconds, seconds)) {
            if (event.packet is ClientboundPlayerInfoUpdatePacket) {
               val joinPacket = event.packet as ClientboundPlayerInfoUpdatePacket
               val joinEntries = joinPacket.newEntries().iterator()

               while (joinEntries.hasNext()) {
                  val entry: Entry = joinEntries.next()
                  if (entry.listed()) {
                     val joiningProfile = entry.profile() ?: continue
                     var joiningName = joiningProfile.name()
                     val localPlayer = ClientAccess.collectiveMinecraft.player!!
                     if (joiningName != localPlayer!!.name.string) {
                        val joinRelationships: SocialRegistry = ClientContext.socialRegistry

                        val joiningRelationship = joinRelationships.getRole(
                           joiningName
                        )
                        if (shouldAnnounceRelationship(joiningRelationship)) {
                           run {
                              val clientSideSettingSnapshot = clientSideSetting
                              val clientSideValue = clientSideSettingSnapshot.value
                              if (clientSideValue) {
                                 val compactSettingSnapshot = compactSetting
                                 val compactValue = compactSettingSnapshot.value
                                 if (compactValue) {
                                    joiningName = joiningName + " joined"
                                    if (joiningRelationship == SocialRole.FRIEND) {
                                       joiningName = joiningName + " [friend]"
                                    } else if (joiningRelationship == SocialRole.ENEMY) {
                                       joiningName = joiningName + " [enemy]"
                                    }

                                    val compactJoinMessage = joiningName

                                    val compactJoinSignature = ChatUtils.createSignature(-254721)
                                    scheduleAnnouncement(compactJoinMessage, compactJoinSignature)
                                    return@run
                                 }
                              }

                              if (joiningRelationship == SocialRole.FRIEND) {
                                 joiningName = "My friend $joiningName"
                              }

                              val joinMessage = "$joiningName has joined the game!"

                              val joinSignature = ChatUtils.createSignature(-254721)
                              scheduleAnnouncement(joinMessage, joinSignature)
                           }

                           announcementTimer.reset()
                        }
                     }
                  }
               }
            }
            if (event.packet is ClientboundPlayerInfoRemovePacket) {
               val leavePacket = event.packet as ClientboundPlayerInfoRemovePacket
               val departingIds = leavePacket.profileIds().iterator()

               while (departingIds.hasNext()) {
                  val departingId: UUID = departingIds.next()
                  val connection = ClientAccess.collectiveMinecraft.player!!.connection
                  val onlinePlayers = connection.listedOnlinePlayers.iterator()

                  while (onlinePlayers.hasNext()) {
                     val onlinePlayer = onlinePlayers.next()
                     val onlineProfile = onlinePlayer.profile
                     if (onlineProfile.id() == departingId) {
                        val departingProfile = onlinePlayer.profile
                        var departingName = departingProfile.name()
                        val leaveRelationships: SocialRegistry = ClientContext.socialRegistry

                        val departingRelationship = leaveRelationships.getRole(
                           departingName
                        )
                        if (shouldAnnounceRelationship(departingRelationship)) {
                           run {
                              val leaveClientSideSetting = clientSideSetting
                              val leaveClientSideEnabled = leaveClientSideSetting.value
                              if (leaveClientSideEnabled) {
                                 val leaveCompactSetting = compactSetting
                                 val leaveCompactEnabled = leaveCompactSetting.value
                                 if (leaveCompactEnabled) {
                                    departingName = departingName + " left"
                                    if (departingRelationship == SocialRole.FRIEND) {
                                       departingName = departingName + " [friend]"
                                    } else if (departingRelationship == SocialRole.ENEMY) {
                                       departingName = departingName + " [enemy]"
                                    }

                                    val compactLeaveMessage = departingName

                                    val compactLeaveSignature = ChatUtils.createSignature(-254722)
                                    scheduleAnnouncement(compactLeaveMessage, compactLeaveSignature)
                                    return@run
                                 }
                              }

                              if (departingRelationship == SocialRole.FRIEND) {
                                 departingName = "My friend $departingName"
                              }

                              val leaveMessage = "$departingName has left the game!"

                              val leaveSignature = ChatUtils.createSignature(-254722)
                              scheduleAnnouncement(leaveMessage, leaveSignature)
                           }

                           announcementTimer.reset()
                        }
                     }
                  }
               }
            }

            return
         }
      }
   }
}
