package me.collective.client.modules.hud

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ScreenChangeEvent
import me.collective.client.events.ConnectScreenConnectEvent
import me.collective.client.events.EventHandler
import me.collective.client.managers.ServerStateManager
import me.collective.client.utils.collections.IntPair
import me.collective.client.utils.ElapsedTimer
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.HudHorizontalAlignment
import me.collective.client.render.ResizableHudWidgetRenderer
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.HudWidgetModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.StringSetting
import me.collective.client.settings.options.HudAnchor
import me.collective.client.settings.options.WelcomerMode

import com.mojang.blaze3d.vertex.PoseStack
import java.lang.invoke.MethodHandles
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.util.Mth

class WelcomerModule : HudWidgetModule("Welcomer") {
   @JvmField
   val modeSetting: Setting<WelcomerMode>

   @JvmField
   val textSetting: Setting<String>

   @JvmField
   val smartSetting: Setting<Boolean>
   private val notifications: MutableList<HudNotification>
   private val connectionScreenTimer: ElapsedTimer
   private var lastMusicNotificationText: String? = null
   private var connectionScreenPending: Boolean = false

   private fun tryAddServerWelcomeNotification(): Boolean {
      val timer: ElapsedTimer = this.connectionScreenTimer
      if (timer.hasElapsed(50L)) {
         return false
      }
      val minecraft: Minecraft = ClientAccess.collectiveMinecraft
      if (minecraft.isLocalServer) {
         return false
      }
      val connection: ClientPacketListener = ClientAccess.collectiveMinecraft.player?.connection
         ?: return false
      val server: ServerData? = connection.getServerData()
      if (server == null) {
         return false
      }
      this.notifications.add(
         HudNotification(
            "Welcome to " + server.ip,
            3000L,
            this.getHudColor(this.renderer.getY())
         )
      )
      return true
   }

   private fun updateSmartNotifications() {
      val smartEnabled: Boolean = this.smartSetting.value!!
      if (!smartEnabled || this.isInHudEditor()) {
         this.finishLagNotification()
         return
      }
      if (this.tryAddServerWelcomeNotification()) {
         return
      }
      this.updateMusicNotification()
      val activeNotifications = this.notifications.iterator()

      while (activeNotifications.hasNext()) {
         val notification: HudNotification = activeNotifications.next()
         if (notification is LagNotification || notification is MusicNotification) {
            return
         }
      }

      var idleDuration: Long = System.currentTimeMillis()
      val networkActivity: ServerStateManager = ClientContext.serverStateManager

      idleDuration -= networkActivity.getLastPacketTime()
      val idleForClamp: Long = idleDuration
      val idleMillis: Long = Math.max(idleForClamp, 0L)
      val lagWidgetType = LagOMeterModule::class.java
      val moduleRegistry: ModuleRegistry = ClientContext.moduleRegistry
      val lagWidget = moduleRegistry.getModule(lagWidgetType) as LagOMeterModule
      if (this.overlapsWidget(lagWidget) &&
         !this.connectionScreenPending
      ) {

         if (idleMillis > 1000L) {
            val minecraft: Minecraft = ClientAccess.collectiveMinecraft
            if (!minecraft.isLocalServer) {
               val player = ClientAccess.collectiveMinecraft.player ?: return
               val playerTicks: Int = player.tickCount

               if (playerTicks >= 40) {
                  val timer: ElapsedTimer = this.connectionScreenTimer

                  if (timer.hasElapsed(500L)) {
                     this.notifications.add(LagNotification())
                     return
                  }
               }
            }
         }
      }

      this.finishLagNotification()
   }

   private fun finishLagNotification() {
      val activeNotifications = this.notifications.iterator()

      while (activeNotifications.hasNext()) {
         val notification: HudNotification = activeNotifications.next()
         if (notification is LagNotification) {

            notification.finish()
            break
         }
      }
   }

   override fun calculateDimensions(): FloatArray {
      var maximumWidth = 0.0f
      var accumulatedProgress = 0.0f
      val hudScale: Float = hud.getTextScale()

      var index = this.notifications.size - 1
      while (index >= 0) {
         val notification: HudNotification = this.notifications[index]

         val transitionMillis = 250.0f
         var progress: Float = this.getNotificationProgress(notification, transitionMillis, 0.0f)

         val scaledProgress: Float = progress * 10.0f

         val maximumProgress = 1.0f
         progress = Math.min(scaledProgress, maximumProgress)

         val easingUpperBound = 1.0f

         val easingRemainder: Float = 1.0f - progress

         progress = easingUpperBound - easingRemainder * (1.0f - progress)
         val textRenderer: FontRenderer = FontRenderer.INSTANCE

         val animatedWidth: Float = textRenderer.getWidth(
            notification.getText()
         ) * progress
         maximumWidth = Math.max(animatedWidth, maximumWidth)
         accumulatedProgress += progress

         if (accumulatedProgress > 1.0f) {
            break
         }
         index--
      }

      return floatArrayOf(
         maximumWidth * hudScale,
         (
            FontRenderer.INSTANCE
               .getHeight() + 1
            ) * hudScale
      )
   }

   private fun overlapsWidget(widget: HudWidgetModule): Boolean {
      if (!widget.isToggled() || this.isInHudEditor()) {
         return false
      }
      if (this.getAnchor() != HudAnchor.NONE) {
         return widget.getAnchor() == this.getAnchor()
      }
      if (this.renderer.getDimensions() == null || widget.getRenderer().getDimensions() == null) {
         return false
      }

      val welcomerPosition = IntPair(this.renderer.getX().toInt(), this.renderer.getY().toInt())
      val widgetPosition = IntPair(widget.getRenderer().getX().toInt(), widget.getRenderer().getY().toInt())
      val distance: Double = welcomerPosition.distanceTo(widgetPosition)
      return distance <= 100.0
   }

   private fun slidesUp(notification: HudNotification): Boolean {

      var time: Long = notification.getStartTimeMillis()

      val evenStartTime: Boolean = time % 2L == 0L
      time = System.currentTimeMillis()

      time -= notification.getStartTimeMillis()

      return if (time <= 500L) !evenStartTime else evenStartTime
   }

   private fun getNotificationProgress(
      notification: HudNotification, transitionMillis: Float, progressOffset: Float
   ): Float {

      var timestamp: Long = notification.getEndTimeMillis()

      val lifetimeMillis: Long = timestamp - notification.getStartTimeMillis()
      timestamp = System.currentTimeMillis()

      val elapsedMillis: Long = timestamp - notification.getStartTimeMillis()

      var progress = 1.0f
      if (elapsedMillis.toFloat() <= transitionMillis) {
         progress = elapsedMillis.toFloat() / transitionMillis
      } else if (elapsedMillis.toFloat() >= lifetimeMillis.toFloat() - transitionMillis) {
         val fadeOutFraction: Float = (lifetimeMillis - elapsedMillis).toFloat() / transitionMillis
         progress = Math.max(fadeOutFraction, 0.0f)
      }

      val offsetProgress: Float = progress - progressOffset

      val maximumProgress = 1.0f
      return Mth.clamp(offsetProgress, 0.0f, maximumProgress)
   }

   override fun renderContent(poses: PoseStack) {

      this.updateSmartNotifications()
      this.notifications.removeIf(
         { notification ->
            val nowMillis: Long = System.currentTimeMillis()

            if (nowMillis < notification.getEndTimeMillis()) {
               val smartEnabled: Boolean = this.smartSetting.value!!
               if (smartEnabled || notification is GreetingNotification) {
                  return@removeIf false
               }
            }

            true
         }
      )
      val dimensions: FloatArray = this.calculateDimensions()
      val hudScale: Float = hud.getTextScale()
      var accumulatedProgress = 0.0f

      var index = this.notifications.size - 1
      while (index >= 0) {
         val notification: HudNotification = this.notifications[index]
         notification.update()

         val opacityTransitionMillis = 250.0f
         val opacity: Float = this.getNotificationProgress(notification, opacityTransitionMillis, accumulatedProgress)

         val slideTransitionMillis = 500.0f
         val progress: Float = this.getNotificationProgress(notification, slideTransitionMillis, 0.0f)
         val slideUp: Boolean = this.slidesUp(notification)
         if (opacity > 0.1) {
            var horizontalOffset = 0.0f
            if (this.renderer
                  .horizontalAlignment == HudHorizontalAlignment.CENTER
            ) {
               val availableWidth: Float = dimensions[0]
               val measurementRenderer: FontRenderer = FontRenderer.INSTANCE

               val remainingWidth: Float = availableWidth
                  - measurementRenderer.getWidth(
                     notification.getText()
                  ) * hudScale

               horizontalOffset = remainingWidth / 2.0f
            }

            val easingRemainder: Double = (1.0f - progress).toDouble()

            val easedProgress: Float = (1.0 - Math.pow(easingRemainder, 5.0)).toFloat()
            val direction: Int = if (slideUp) -1 else 1

            val signedSlideDistance: Float = (direction * -5).toFloat()
            var verticalOffset: Float = signedSlideDistance * (1.0f - easedProgress)
            verticalOffset *= hudScale
            val textRenderer: FontRenderer = FontRenderer.INSTANCE

            val text: String = notification.getText()
            val textX: Float = horizontalOffset / hudScale
            textRenderer.drawWithShadow(
               poses,
               text,
               textX,
               verticalOffset,
               hudScale,
               ColorUtils.withAlpha(
                  notification.getColor()!!, opacity
               )
            )
         }

         accumulatedProgress += opacity

         if (accumulatedProgress >= 1.0f) {
            break
         }
         index--
      }
   }

   @EventHandler
   private fun onMinecraftSetScreenEvent(event: ScreenChangeEvent.Before) {
      try {
         MethodHandles.lookup().ensureInitialized(Screen::class.java)
      } catch (failure: IllegalAccessException) {
         val decoded = RuntimeException(failure.toString())
         throw RuntimeException(decoded.javaClass.name + " :  null  : " + decoded)
      }
      if (event.newScreen == null &&
         this.connectionScreenPending
      ) {
         this.connectionScreenPending = false
         this.connectionScreenTimer.reset()
      }
   }

   fun shouldHideWidget(widget: HudWidgetModule): Boolean {
      if (!this.isToggled()) {
         return false
      }
      val smartEnabled: Boolean = this.smartSetting.value!!
      return smartEnabled && this.overlapsWidget(widget) && !this.isInHudEditor()
   }

   @EventHandler
   private fun onConnectScreenConnectEvent(event: ConnectScreenConnectEvent) {
      this.connectionScreenPending = true
   }

   private fun updateMusicNotification() {
      val musicWidgetType = MusicModule::class.java
      val moduleRegistry: ModuleRegistry = ClientContext.moduleRegistry
      val musicWidget = moduleRegistry.getModule(musicWidgetType) as MusicModule
      if (!this.isInHudEditor()) {
         var statusOrTextLength: Int = MediaSessionBridge.playbackStatus

         if (statusOrTextLength == 4) {
            if (this.overlapsWidget(musicWidget)) {
               val mediaDisplayText: String = MediaSessionBridge.getDisplayText()
               val text: String = mediaDisplayText.trim()
               statusOrTextLength = text.length

               if (statusOrTextLength < 3) {
                  this.lastMusicNotificationText = null
                  return
               }

               if (text == this.lastMusicNotificationText) {
                  return
               }

               this.notifications.add(
                  MusicNotification(
                     "♪ $text",
                     3000L,
                     this.getHudColor(
                        this.renderer
                           .getY()
                     )
                  )
               )
               this.lastMusicNotificationText = text
               return
            }
         }

         this.lastMusicNotificationText = null
      }
   }

   init {
      this.modeSetting = this.add(EnumSetting<WelcomerMode>("Mode", WelcomerMode.NAME))

      val textOption = StringSetting("Text", "Welcome to Collective.") { this.modeSetting.value == WelcomerMode.CUSTOM }
      this.textSetting = this.add(textOption)

      this.smartSetting = this.add(BooleanSetting("Smart", true))
      this.notifications = ArrayList()
      this.connectionScreenTimer = ElapsedTimer()
      val widget = ResizableHudWidgetRenderer(this)
      this.setRenderer(widget)
      this.notifications.add(GreetingNotification(this))
   }

}
