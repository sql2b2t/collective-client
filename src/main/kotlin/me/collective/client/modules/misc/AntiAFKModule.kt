package me.collective.client.modules.misc

import me.collective.client.utils.math.MathConstants
import java.util.function.Predicate
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ChatMessageEvent
import me.collective.client.events.InputPressEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MouseHandlerTurnPlayerEvent
import me.collective.client.managers.InputOverrideManager
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.events.EventStage
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import me.collective.mixin.MinecraftAccessor
import java.util.ArrayList
import java.util.Collections
import java.util.Random
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import net.minecraft.client.KeyMapping
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.thread.BlockableEventLoop
import net.minecraft.world.InteractionHand

class AntiAFKModule : Module("AntiAFK", "Prevents you from being kicked for AFK-ing.", ModuleCategory.MISC) {
   private val delaySetting: Setting<Float> = this.add(NumberSetting<Float>("Delay", 15.0f, 1.0f, 60.0f).withUnit("s"))
   private val autoReplySetting: Setting<Boolean> = this.add(BooleanSetting("AutoReply", false))
   private val jumpSetting: Setting<Boolean> = this.add(BooleanSetting("Jump", false))
   private val sneakSetting: Setting<Boolean> = this.add(BooleanSetting("Sneak", false))
   private val rotateSetting: Setting<Boolean> = this.add(BooleanSetting("Rotate", false))
   internal val attackSetting: Setting<Boolean> = this.add(BooleanSetting("Attack", false))
   private val onlySwingSetting: Setting<Boolean> = this.add(
      BooleanSetting("OnlySwing", false, Predicate<Any> { this.attackSetting.value })
   )
   private val idleTimer: ElapsedTimer = ElapsedTimer()
   private val actionTimer: ElapsedTimer = ElapsedTimer()
   private var jumpActionPending: Boolean = false
   private var sneakActionPending: Boolean = false

   private fun createActions(): List<Runnable> {
      val actions = ArrayList<Runnable>()

      val jumpEnabled = this.jumpSetting.value
      if (jumpEnabled) {
         actions.add(Runnable { this.jumpActionPending = true })
      }

      val sneakEnabled = this.sneakSetting.value
      if (sneakEnabled) {
         actions.add(Runnable { this.sneakActionPending = true })
      }

      val rotateEnabled = this.rotateSetting.value
      if (rotateEnabled) {
         val rotationRandom = Random()
         actions.add(
            Runnable {
               val rotationPlayerSnapshot = ClientAccess.collectiveMinecraft.player ?: return@Runnable
               val yaw = rotationRandom.nextFloat(
                  MathConstants.FULL_TURN_DEGREES.toFloat()
               )
               rotationPlayerSnapshot.setYRot(yaw)

               val pitch = rotationRandom.nextFloat(180f)
                  - MathConstants.NINETY_DEGREES
               rotationPlayerSnapshot.setXRot(pitch)
            }
         )
      }

      val attackEnabled = this.attackSetting.value
      if (attackEnabled) {
         actions.add(Runnable {
            val onlySwing = this.onlySwingSetting.value
            if (onlySwing) {
               val swingPlayer = ClientAccess.collectiveMinecraft.player ?: return@Runnable
               val swingHand = InteractionHand.MAIN_HAND
               swingPlayer.swing(swingHand)
            } else {
               (ClientAccess.collectiveMinecraft as MinecraftAccessor).attack()
            }
         })
      }

      return actions
   }

   private fun isIdle(): Boolean {
      val timerSnapshot = this.idleTimer
      val delaySeconds = this.delaySetting.value

      val delayMillis = (delaySeconds * 1e+03F).toLong()

      return timerSnapshot.hasElapsed(delayMillis)
   }

   override fun onEnable() {
      this.idleTimer.reset()
      this.actionTimer.reset()
   }

   @EventHandler
   private fun onInputActivity(event: InputPressEvent) {
      this.idleTimer.reset()
   }

   @EventHandler
   private fun onMouseHandlerTurnPlayerEvent(event: MouseHandlerTurnPlayerEvent) {
      this.idleTimer.reset()
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      if (!this.isIdle()) {
         return
      }
      val jumpKeySnapshot: KeyMapping = ClientAccess.collectiveMinecraft.options.keyJump
      if (jumpKeySnapshot.isDown) {
         val jumpEnabled = this.jumpSetting.value
         if (jumpEnabled) {
            val jumpPressed = false
            val jumpKey: KeyMapping = ClientAccess.collectiveMinecraft.options.keyJump
            jumpKey.setDown(jumpPressed)
         }
      }

      val timerCapture = this.actionTimer
      val delayValue = this.delaySetting.value
      val delaySecondsCapture = delayValue.toDouble()
      val delayUnit = TimeUnit.SECONDS

      if (timerCapture.hasElapsed(delaySecondsCapture, delayUnit)) {
         val actions = this.createActions()
         Collections.shuffle(actions)
         if (!actions.isEmpty()) {
            actions[0].run()
         }

         this.actionTimer.reset()
      }

      if (this.jumpActionPending) {
         val jumpOverridesSnapshot: InputOverrideManager = ClientContext.inputOverrides
         jumpOverridesSnapshot.requestJump(true)
      }

      if (this.sneakActionPending) {
         val sneakOverridesSnapshot: InputOverrideManager = ClientContext.inputOverrides
         sneakOverridesSnapshot.requestSneak(false)
      }

      this.jumpActionPending = false
      this.sneakActionPending = false
   }

   @EventHandler(priority = 100)
   private fun onIncomingMessage(event: ChatMessageEvent) {
      if (event.isCancelled) {
         return
      }
      val autoReplyEnabled = this.autoReplySetting.value
      if (!autoReplyEnabled) {
         return
      }
      if (!this.isIdle()) {
         return
      }
      if (event.stage != EventStage.Pre || event.message == null) {
         return
      }
      val messageCapture = event.message!!.string
      if (!ChatUtils.isIncomingWhisper(messageCapture)) {
         return
      }

      val replyClient: BlockableEventLoop<*> = ClientAccess.collectiveMinecraft as BlockableEventLoop<*>
      val replyAction = Runnable {
         val replyPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return@Runnable
         val replyConnectionCapture: ClientPacketListener = replyPlayer.connection
         val playerName = replyPlayer.name.string
         val randomSample = Math.random()
         val replySuffixValue = randomSample * 10000.0
         val replySuffix = Math.floor(replySuffixValue).toInt()
         var replyCommand = "r Hello! This is auto reply talking. \u0001 is currently AFK :'). [\u0001]"

         replyCommand = replyCommand.replaceFirst("\u0001", Matcher.quoteReplacement(playerName))
         replyCommand = replyCommand.replaceFirst("\u0001", replySuffix.toString())
         replyConnectionCapture.sendCommand(replyCommand)
      }
      replyClient.executeIfPossible(replyAction)
   }
}
