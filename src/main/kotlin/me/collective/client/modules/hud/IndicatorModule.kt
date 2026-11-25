package me.collective.client.modules.hud


import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LivingEntityAddEffectEvent
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.render.ResizableHudWidgetRenderer
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.movement.WarpModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.FloatAnimation
import java.awt.Color
import java.util.ArrayList
import java.util.EnumMap
import net.minecraft.ChatFormatting
import net.minecraft.core.Holder
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

class IndicatorModule : HudWidgetModule("Indicator") {
   private val warpSetting: Setting<Boolean>
   private val resistanceSetting: Setting<Boolean>
   private val strengthSetting: Setting<Boolean>
   private val renderSetting: Setting<RenderMode>
   private val layoutSetting: Setting<Layout>
   private val vanillaSetting: Setting<Boolean>
   private val widthSetting: Setting<Float>
   private val prefixSetting: Setting<Boolean>

   @JvmField
   val hideSetting: Setting<Boolean>

   private val warpChargeAnimation = FloatAnimation()
   private val resistance = EffectIndicator("Resistance", MobEffects.DAMAGE_RESISTANCE, 2)
   private val strength = EffectIndicator("Strength", MobEffects.DAMAGE_BOOST, 0)
   private val visibilityAnimations: MutableMap<DisplayMode, FloatAnimation> = EnumMap(DisplayMode::class.java)

   init {
      this.renderSetting = this.add(EnumSetting("Render", RenderMode.PROGRESSBAR))
      this.layoutSetting = this.add(EnumSetting("Layout", Layout.VERTICAL))
      this.vanillaSetting = this.add(BooleanSetting("Vanilla", false,
         { this.renderSetting.getValue() == RenderMode.PROGRESSBAR }))
      this.widthSetting = this.add(NumberSetting("Width", 32.0f, 16.0f, 48.0f,
         { this.renderSetting.getValue() == RenderMode.PROGRESSBAR }))
      this.prefixSetting = this.add(BooleanSetting("Prefix", true,
         { this.renderSetting.getValue() == RenderMode.TEXT }))
      this.hideSetting = this.add(BooleanSetting("Hide", false))
      val indicators = this.add(BooleanSetting("Indicators", false).markAsGroup())
      this.warpSetting = this.add(BooleanSetting("Warp", true).dependsOn(indicators))
      this.resistanceSetting = this.add(BooleanSetting("Resistance", false).dependsOn(indicators))
      this.strengthSetting = this.add(BooleanSetting("Strength", false).dependsOn(indicators))
      for (mode in DisplayMode.values()) {
         this.visibilityAnimations[mode] = FloatAnimation()
      }
      this.setRenderer(ResizableHudWidgetRenderer(this))
   }

   override fun fromJson(json: JsonElement) {
      val module = json as? JsonObject
      val settings = module?.get("settings") as? JsonObject
      if (module != null && settings != null &&
         (settings.has("Display") || settings.get("Indicators") is JsonArray)) {
         val migrated = module.deepCopy()
         var selected = JsonArray()
         val array = settings.get("Indicators") as? JsonArray
         if (array != null) selected = array
         else selected.add(settings.get("Display"))
         val migratedSettings = migrated.getAsJsonObject("settings")
         migratedSettings.remove("Indicators")
         for (mode in DisplayMode.values()) {
            migratedSettings.addProperty(mode.getName(), false)
         }
         for (entry in selected) {
            var matched: DisplayMode? = null
            for (mode in DisplayMode.values()) {
               if (mode.getName().equals(entry.getAsString(), ignoreCase = true)) matched = mode
            }
            if (matched == null) throw IllegalArgumentException("Unknown indicator: " + entry.getAsString())
            migratedSettings.addProperty(matched.getName(), true)
         }
         super.fromJson(migrated)
      } else {
         super.fromJson(json)
      }
   }

   private fun selectedIndicators(): List<DisplayMode> {
      val selected = ArrayList<DisplayMode>()
      if (this.warpSetting.getValue()) selected.add(DisplayMode.WARP)
      if (this.resistanceSetting.getValue()) selected.add(DisplayMode.RESISTANCE)
      if (this.strengthSetting.getValue()) selected.add(DisplayMode.STRENGTH)
      return selected
   }

   private fun getDisplayData(mode: DisplayMode): DisplayData {
      return when (mode) {
         DisplayMode.WARP -> this.getWarpData()
         DisplayMode.RESISTANCE -> this.resistance.getData()
         DisplayMode.STRENGTH -> this.strength.getData()
      }
   }

   private fun getWarpData(): DisplayData {
      val requiredCharge = warp.chargeSetting.getValue()
      val progress = if (requiredCharge <= 0) 1.0f
         else Mth.clamp(this.warpChargeAnimation.getValue() / requiredCharge, 0.0f, 1.0f)
      val eased = if (progress < 0.5f) 2.0f * progress * progress
         else (1.0 - Math.pow((-2.0f * progress + 2.0f).toDouble(), 2.0) / 2.0).toFloat()
      return DisplayData("Warp", warp.getCharge().toString(), eased)
   }

   private fun indicatorWidth(data: DisplayData): Float {
      if (this.renderSetting.getValue() == RenderMode.TEXT) {
         return FontRenderer.INSTANCE.getWidth(data.formatText(this.prefixSetting.getValue()))
      }
      return this.widthSetting.getValue() + 0.5f
   }

   private fun indicatorHeight(): Float {
      if (this.renderSetting.getValue() == RenderMode.TEXT) {
         return FontRenderer.INSTANCE.getHeight().toFloat()
      }
      return if (this.vanillaSetting.getValue()) 5.0f else 3.0f
   }

   override fun calculateDimensions(): FloatArray {
      if (ClientAccess.collectiveMinecraft.player == null) {
         return floatArrayOf(0.0f, 0.0f)
      }
      var width = 0.0f
      var height = 0.0f
      var count = 0
      val horizontal = this.layoutSetting.getValue() == Layout.HORIZONTAL
      for (mode in this.selectedIndicators()) {
         val itemWidth = this.indicatorWidth(this.getDisplayData(mode))
         val itemHeight = this.indicatorHeight()
         width = if (horizontal) width + itemWidth else Math.max(width, itemWidth)
         height = if (horizontal) Math.max(height, itemHeight) else height + itemHeight
         count++
      }
      if (count > 1) {
         if (horizontal) width += (count - 1) * 4.0f
         else height += (count - 1) * 4.0f
      }
      val scale = hud.getTextScale()
      return floatArrayOf(width * scale, height * scale)
   }

   override fun renderContent(poses: PoseStack) {
      if (ClientAccess.collectiveMinecraft.player == null) return
      this.warpChargeAnimation.setTarget(warp.getCharge().toFloat(), 100L)
      poses.pushPose()
      try {
         val scale = hud.getTextScale()
         poses.scale(scale, scale, 1.0f)
         var x = 0.0f
         var y = 0.0f
         for (mode in this.selectedIndicators()) {
            val data = this.getDisplayData(mode)
            val visibility = this.visibilityAnimations[mode]!!
            val hidden = this.hideSetting.getValue() &&
         (if (mode == DisplayMode.WARP) data.progress >= 1.0f else data.progress <= 0.0f)
            if (hidden) visibility.setTarget(0.0f, 500L)
            else visibility.setValue(1.0f)
            val opacity = visibility.getValue()
            if (this.renderSetting.getValue() == RenderMode.TEXT) {
               FontRenderer.INSTANCE.drawWithShadow(poses,
                  data.formatText(this.prefixSetting.getValue()), x, y,
                  ColorUtils.withAlpha(this.getHudColor(y), opacity))
            } else {
               this.renderBar(poses, mode, data.progress, x, y, opacity)
            }
            if (this.layoutSetting.getValue() == Layout.HORIZONTAL) x += this.indicatorWidth(data) + 4.0f
            else y += this.indicatorHeight() + 4.0f
         }
      } finally {
         poses.popPose()
      }
   }

   private fun renderBar(poses: PoseStack, mode: DisplayMode, progress: Float, x: Float, y: Float, opacity: Float) {
      val width = this.widthSetting.getValue()
      val clamped = Mth.clamp(progress, 0.0f, 1.0f)
      if (this.vanillaSetting.getValue()) {

         val fill: Color = when (mode) {
            DisplayMode.WARP -> Color(128, 255, 32)
            DisplayMode.RESISTANCE -> Color(MobEffects.DAMAGE_RESISTANCE.value().getColor())
            DisplayMode.STRENGTH -> Color(MobEffects.DAMAGE_BOOST.value().getColor())
         }
         GuiDrawUtils.fillRect(poses, x, y, x + width + 0.5f, y + 5.0f,
            ColorUtils.withAlpha(Color.black, opacity))
         GuiDrawUtils.fillRect(poses, x + 0.5f, y + 0.5f, x + 0.5f + Math.max(width * clamped - 0.5f, 0.0f), y + 4.5f,
            ColorUtils.withAlpha(fill, opacity))
         return
      }
      val base = ColorUtils.withAlpha(this.getHudColor(y + 9.0f), opacity)
      val background = Color(ColorUtils.modulateRgb(base.hashCode(), 63, 63, 63))
      val fill = ColorUtils.withAlpha(base, 0.7f * opacity)
      GuiDrawUtils.fillRect(poses, x, y, x + width + 0.5f, y + 3.0f,
         ColorUtils.withAlpha(background, 0.7f * opacity))
      val fillWidth = Math.max(width * clamped - 0.5f, 0.0f)
      GuiDrawUtils.fillHorizontalGradient(poses, x + 0.5f, y + 0.5f, fillWidth, 2.0f,
         ColorUtils.darken(fill, 0.83f).hashCode(), fill.hashCode())
   }

   @EventHandler
   private fun onLivingEntityAddEffectEvent(event: LivingEntityAddEffectEvent) {
      val incoming = event.effect
      if (ClientAccess.collectiveMinecraft.player == null) return
      this.resistance.update(incoming)
      this.strength.update(incoming)
   }

   enum class RenderMode(private val displayName: String) : NamedEntry {
      TEXT("Text"), PROGRESSBAR("ProgressBar");

      override fun getName(): String {
         return this.displayName
      }
   }

   enum class DisplayMode(private val displayName: String) : NamedEntry {
      WARP("Warp"), RESISTANCE("Resistance"), STRENGTH("Strength");

      override fun getName(): String {
         return this.displayName
      }
   }

   enum class Layout(private val displayName: String) : NamedEntry {
      HORIZONTAL("Horizontal"), VERTICAL("Vertical");

      override fun getName(): String {
         return this.displayName
      }
   }

   private class EffectIndicator(
      private val name: String,
      private val effect: Holder<MobEffect>,
      private val minimumAmplifier: Int
   ) {
      private val animation = FloatAnimation()
      private var durationTicks: Int = 0

      fun update(incoming: MobEffectInstance) {
         if (incoming.getEffect() !== this.effect || incoming.getAmplifier() < this.minimumAmplifier) return
         val current = ClientAccess.collectiveMinecraft.player!!.getEffect(this.effect)
         if (current == null || current.getAmplifier() < this.minimumAmplifier ||
         incoming.getAmplifier() > current.getAmplifier() ||
         incoming.getAmplifier() == current.getAmplifier() && hasShorterDuration(current, incoming)) {
            this.durationTicks = incoming.getDuration()
            this.animation.setValue(1.0f)
         }
      }

      fun getData(): DisplayData {
         val current = ClientAccess.collectiveMinecraft.player!!.getEffect(this.effect)
         if (current != null && current.getAmplifier() >= this.minimumAmplifier && current.isInfiniteDuration()) {
            this.animation.setValue(1.0f)
            return DisplayData(this.name, "∞", 1.0f)
         }
         val duration = if (current != null && current.getAmplifier() >= this.minimumAmplifier) current.getDuration() else 0
         if (duration > this.durationTicks) this.durationTicks = duration
         val remaining = if (this.durationTicks <= 0) 0.0f
            else Mth.clamp((duration / 20 * 20).toFloat() / this.durationTicks, 0.0f, 1.0f)
         this.animation.setTarget(remaining, 500L)
         val progress = this.animation.getValue { sample: Float -> (1.0 - Math.pow((1.0f - sample).toDouble(), 3.0)).toFloat() }
         return DisplayData(this.name, Math.ceil((duration / 20.0f).toDouble()).toInt().toString() + "s", progress)
      }

      private fun hasShorterDuration(current: MobEffectInstance, incoming: MobEffectInstance): Boolean {
         return !current.isInfiniteDuration() &&
         (current.getDuration() < incoming.getDuration() || incoming.isInfiniteDuration())
      }
   }

   private data class DisplayData(val label: String, val valueText: String, val progress: Float) {
      fun formatText(prefix: Boolean): String {
         return if (prefix) this.label + ChatFormatting.WHITE.toString() + " " + this.valueText else this.valueText
      }
   }

   companion object {
      private val warp: WarpModule get() = ClientContext.getModule(WarpModule::class.java)
   }
}
