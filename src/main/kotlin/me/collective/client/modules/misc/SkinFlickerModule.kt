package me.collective.client.modules.misc


import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.utils.ElapsedTimer
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import net.minecraft.client.Options
import net.minecraft.world.entity.player.PlayerModelPart

class SkinFlickerModule : Module("SkinFlicker", "Flicks your skin parts.", ModuleCategory.MISC, "skinblinker") {
   private val modeSetting: Setting<ModeChoice>
   private val capeSetting: Setting<Boolean>
   private val delaySetting: Setting<Float>
   private val savedModelParts: MutableMap<PlayerModelPart, Boolean>
   private val modelPartCount: Int
   private val flickerTimer: ElapsedTimer
   private var optionsDirty: Boolean = false
   private var sequenceIndex: Int = 0

   override fun onEnable() {

      for (part in PlayerModelPart.entries) {
         val savedPartsSnapshot = this.savedModelParts
         val options = ClientAccess.collectiveMinecraft.options
         val enabled = options.isModelPartEnabled(part)
         savedPartsSnapshot[part] = enabled
      }
   }

   override fun onDisable() {
      val savedPartsSnapshot = this.savedModelParts
      val options = ClientAccess.collectiveMinecraft.options
      savedPartsSnapshot.forEach { (part, enabled) -> options.setModelPart(part, enabled) }
   }

   @EventHandler
   private fun onPlayerTick(event: LocalPlayerTickEvent) {
      var timerSnapshot = this.flickerTimer
      val delaySettingSnapshot = this.delaySetting
      val delayValue = delaySettingSnapshot.value
      val delaySeconds = delayValue.toDouble()
      val delayUnit = TimeUnit.SECONDS
      if (timerSnapshot.hasElapsed(delaySeconds, delayUnit)) {
         val modeSettingSnapshot = this.modeSetting
         val mode = modeSettingSnapshot.value
         when (mode) {
            ModeChoice.FULL -> {

               for (part in PlayerModelPart.entries) {
                  if (this.shouldTogglePart(part)) {
                     this.togglePart(part)
                  }
               }
            }
            ModeChoice.VERTICAL, ModeChoice.HORIZONTAL -> {
               val sequenceModeSnapshot = this.modeSetting
               val partOrder = if (sequenceModeSnapshot.value == ModeChoice.HORIZONTAL)
                  HORIZONTAL_PART_ORDER
               else
                  VERTICAL_PART_ORDER
               val indexSnapshot = this.sequenceIndex

               val sequencePart = partOrder[indexSnapshot % (6)]
               if (this.shouldTogglePart(sequencePart)) {
                  this.togglePart(sequencePart)
                  val nextIndex = this.sequenceIndex + 1

                  this.sequenceIndex = nextIndex % (6)
               }
            }
            ModeChoice.RANDOM -> {
               val allParts = PlayerModelPart.entries
               val randomSnapshot = ThreadLocalRandom.current()
               val randomBound = this.modelPartCount
               val randomPart = allParts[randomSnapshot.nextInt(randomBound)]
               if (this.shouldTogglePart(randomPart)) {
                  this.togglePart(randomPart)
               }
            }
         }

         if (this.optionsDirty) {
            val options = ClientAccess.collectiveMinecraft.options
            options.broadcastOptions()
            this.optionsDirty = false
         }

         timerSnapshot = this.flickerTimer

         timerSnapshot.reset()
      }
   }

   private fun togglePart(part: PlayerModelPart) {
      this.optionsDirty = true
      val writeOptionsSnapshot = ClientAccess.collectiveMinecraft.options
      val readOptions = ClientAccess.collectiveMinecraft.options
      val enabled = !readOptions.isModelPartEnabled(part)
      writeOptionsSnapshot.setModelPart(part, enabled)
   }

   private fun shouldTogglePart(part: PlayerModelPart): Boolean {
      val capeSettingSnapshot = this.capeSetting
      val includeCape = capeSettingSnapshot.value
      return includeCape || part != PlayerModelPart.CAPE
   }

   init {
      modeSetting = add(EnumSetting("Mode", ModeChoice.FULL))
      capeSetting = add(BooleanSetting("Cape", false))
      delaySetting = add(NumberSetting("Delay", 1.0f, 0.0f, 10.0f).withUnit("s"))
      savedModelParts = HashMap()
      modelPartCount = PlayerModelPart.entries.size
      flickerTimer = ElapsedTimer()
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      FULL("Full"),
      VERTICAL("Vertical"),
      HORIZONTAL("Horizontal"),
      RANDOM("Random");

      override fun getName(): String {
         return this.displayName
      }
   }

   companion object {

      private val HORIZONTAL_PART_ORDER: Array<PlayerModelPart>
      private val VERTICAL_PART_ORDER: Array<PlayerModelPart>

      init {

         HORIZONTAL_PART_ORDER = arrayOf(
            PlayerModelPart.LEFT_SLEEVE,
            PlayerModelPart.JACKET,
            PlayerModelPart.HAT,
            PlayerModelPart.LEFT_PANTS_LEG,
            PlayerModelPart.RIGHT_PANTS_LEG,
            PlayerModelPart.RIGHT_SLEEVE
         )

         VERTICAL_PART_ORDER = arrayOf(
            PlayerModelPart.HAT,
            PlayerModelPart.JACKET,
            PlayerModelPart.LEFT_SLEEVE,
            PlayerModelPart.RIGHT_SLEEVE,
            PlayerModelPart.LEFT_PANTS_LEG,
            PlayerModelPart.RIGHT_PANTS_LEG
         )
      }
   }
}
