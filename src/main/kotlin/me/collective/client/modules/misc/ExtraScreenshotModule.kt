package me.collective.client.modules.misc


import java.util.function.Predicate
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.audio.BuiltinSound
import me.collective.client.utils.ElapsedTimer
import me.collective.client.render.FontRenderer
import me.collective.client.audio.SoundClip
import me.collective.client.managers.SoundManager
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.collections.NamedIdentifier
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.IdentifierSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.network.HttpRequests

import com.google.gson.JsonObject
import com.mojang.blaze3d.platform.NativeImage
import java.awt.Graphics2D
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.nio.channels.Channels
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import javax.imageio.ImageIO
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component

class ExtraScreenshotModule : Module("ExtraScreenshot", "Uploads your screenshots to Imgur/clipboard.", ModuleCategory.MISC) {
   val modeSetting: Setting<ModeChoice> = this.add(EnumSetting<ModeChoice>("Mode", ModeChoice.COPY))
   val deleteFileSetting: Setting<Boolean> = this.add(
      BooleanSetting("DeleteFile", false, Predicate<Any> { this.modeSetting.value != ModeChoice.NONE })
   )
   private val soundSetting: Setting<Boolean> = this.add(BooleanSetting("Sound", false).markVisibilityDependency())
   private val pathSetting: Setting<NamedIdentifier> = this.add(IdentifierSetting("Path", BuiltinSound.WARNING.identifier).dependsOn(this.soundSetting))
   private val volumeSetting: Setting<Float> = this.add(NumberSetting<Float>("Volume", 1.0f, 0.0f, 1.0f).dependsOn(this.soundSetting))
   private val uploadTimer: ElapsedTimer = ElapsedTimer()
   private var cooldownMessageIndex: Int = 0

   private fun playScreenshotSound() {
      val playSound = this.soundSetting.value
      if (playSound) {
         val soundManagerSnapshot = ClientContext.soundManager
         val soundId = this.pathSetting.value
         val soundSnapshot: SoundClip = soundManagerSnapshot.getSound(soundId)
         val soundVolume = this.volumeSetting.value
         soundSnapshot.play(soundVolume)
      }
   }

   fun isModeNone(): Boolean {
      return this.modeSetting.value == ModeChoice.NONE
   }

   fun processScreenshotBytes(imageBytes: ByteArray) {
      val mode = this.modeSetting.value
      if (mode == ModeChoice.IMGUR) {
         synchronized(this) {
            if (!this.uploadTimer.hasElapsed(uploadCooldownMillis)) {
               this.cooldownMessageIndex = (this.cooldownMessageIndex + 1) % 3
               val remaining = uploadCooldownMillis - this.uploadTimer.elapsedMillis()
               val message = Component.literal("Wait " + remaining + "ms before uploading a screenshot").withStyle(ChatFormatting.YELLOW)
               ChatUtils.printChat(message, ChatUtils.createSignature(-34596741 + this.cooldownMessageIndex), ChatUtils.MessagePriority.LOW)
               return
            }
            this.uploadTimer.reset()
         }
      }
      try { this.playScreenshotSound() }
      catch (failure: Exception) { LOGGER.warn("Could not play screenshot sound", failure) }
      if (mode != ModeChoice.IMGUR) this.uploadTimer.reset()
      if (mode == ModeChoice.NONE) return
      try {
         if (mode == ModeChoice.COPY) {
            val source = ImageIO.read(ByteArrayInputStream(imageBytes)) ?: throw IOException("Unsupported screenshot format")
            val image = BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB)
            val graphics: Graphics2D = image.createGraphics()
            try { graphics.drawImage(source, 0, 0, null) }
            finally { graphics.dispose() }
            this.copyImageToClipboard(ClipboardImage(image))
            ChatUtils.printChat(Component.literal("Copied screenshot to clipboard."), ChatUtils.moduleSignature(this))
            return
         }
         ChatUtils.printChat(Component.literal("Uploading screenshot..."), ChatUtils.moduleSignature(this))
         val encoded = URLEncoder.encode(Base64.getEncoder().encodeToString(imageBytes), StandardCharsets.UTF_8)
         val request = HttpRequests.post("https://api.imgur.com/3/image", HttpRequest.BodyPublishers.ofString("image=" + encoded))
            .header("Content-Type", "application/x-www-form-urlencoded").header("Authorization", imgurAuthorization)
            .timeout(Duration.ofSeconds(10)).build()
         val response = HttpRequests.send(request)
         if (response.statusCode() / 100 != 2) throw IOException("Imgur returned HTTP " + response.statusCode())
         val json = ClientAccess.gson.fromJson(response.body(), JsonObject::class.java)
         if (json == null || !json.has("success") || !json.get("success").asBoolean || !json.has("data")) {
            throw IOException("Invalid upload response")
         }
         val link = json.getAsJsonObject("data").get("link").asString
         val uri = URI.create(link)
         if (!"https".equals(uri.scheme, ignoreCase = true) && !"http".equals(uri.scheme, ignoreCase = true)) {
            throw IOException("Invalid image link")
         }
         try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(link), null)
         } catch (clipboardFailure: Throwable) {
            // AWT-буфер недоступен на части Linux/Wayland/без дисплея —
            // текстовую ссылку кладём через GLFW-буфер обмена Minecraft.
            ClientAccess.collectiveMinecraft.keyboardHandler.setClipboard(link)
         }
         val notification = Component.literal("Copied screenshot link.").withStyle { style -> style.withClickEvent(ClickEvent.OpenUrl(uri)) }
         ChatUtils.printChat(notification, -43296436)
      } catch (failure: Exception) {
         LOGGER.warn("Screenshot {} failed", if (mode == ModeChoice.COPY) "copy" else "upload", failure)
         val message = Component.literal(if (mode == ModeChoice.COPY) "Failed to copy the screenshot!" else "Failed to upload screenshot to Imgur")
            .withStyle(ChatFormatting.RED)
         ChatUtils.printChat(message, ChatUtils.moduleSignature(this), ChatUtils.MessagePriority.MID)
      }
   }

   override fun getInfo(): String? {
      val modeOption = this.modeSetting
      return FontRenderer.capitalize(modeOption.value)
   }

   override fun onEnable() {
      this.cooldownMessageIndex = 0
   }

   fun shouldDeleteFile(): Boolean {
      val modeOption = this.modeSetting
      if (modeOption.value == ModeChoice.NONE) {
         return false
      }
      return this.deleteFileSetting.value
   }

   private fun copyImageToClipboard(image: ClipboardImage) {
      val toolkit = Toolkit.getDefaultToolkit()
      val clipboard: Clipboard = toolkit.systemClipboard
      clipboard.setContents(image, null)
   }

   fun processScreenshot(image: NativeImage) {
      val imageBytes: ByteArray
      try {
         try {
            imageBytes = this.encodeScreenshot(image)
         } catch (encodingFailure: Exception) {
            val message: Component = Component.nullToEmpty("Failed to save the screenshot")
            ChatUtils.printChat(message, ChatUtils.moduleSignature(this), ChatUtils.MessagePriority.MID)
            return
         }
      } finally {
         if (this.shouldDeleteFile()) {
            image.close()
         }
      }

      ClientAccess.executorService.submit(Runnable {
         try {
            this.processScreenshotBytes(imageBytes)
         } catch (processingFailure: Exception) {
            LOGGER.warn("Could not process screenshot", processingFailure)
         }
      })
   }

   private fun encodeScreenshot(image: NativeImage): ByteArray {
      val output = ByteArrayOutputStream()
      Channels.newChannel(output).use { channel ->
         if (!image.writeToChannel(channel)) throw IOException("Screenshot encoding failed")
      }
      return output.toByteArray()
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      NONE("None"),
      COPY("Copy"),
      IMGUR("Imgur");

      override fun getName(): String {
         return this.displayName
      }
   }

   companion object {
      private val LOGGER: org.slf4j.Logger = com.mojang.logging.LogUtils.getLogger()
      private val imgurAuthorization = "Client-ID e7c0b6a4c926098"
      private val uploadCooldownMillis: Long = 1500L
      private val uploadClient: HttpClient = HttpClient.newHttpClient()
   }
}
