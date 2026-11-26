package me.collective.client.modules.render


import java.util.function.Predicate
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.render.ShaderPass
import me.collective.client.managers.ShaderRegistry
import me.collective.client.render.RenderUtils
import me.collective.client.render.GameOptionsOverrides
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import com.mojang.blaze3d.pipeline.RenderTarget
import me.collective.mixin.DimensionTypeAccessor
import java.awt.Color
import java.util.Calendar
import java.util.function.Supplier
import net.minecraft.client.OptionInstance
import net.minecraft.client.Options
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.core.Holder
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.level.biome.Biome.Precipitation

class AmbienceModule : Module("Ambience", "Changes several ambient things.", ModuleCategory.RENDER, "fullbright") {
   @JvmField
   val brightnessSetting: Setting<BrightnessChoice>
   @JvmField
   val colorSetting: Setting<Color>
   @JvmField
   val occlusionSetting: Setting<Boolean>
   @JvmField
   val occlusionColorSetting: Setting<Color>
   @JvmField
   val worldTimeSetting: Setting<Boolean>
   private val syncSetting: Setting<Boolean>
   private val timeSetting: Setting<Float>
   @JvmField
   val worldWeatherSetting: Setting<Boolean>
   @JvmField
   val weatherSetting: Setting<WeatherChoice>
   @JvmField
   val weatherAlwaysSetting: Setting<Boolean>
   @JvmField
   val opacitySetting: Setting<Int>
   @JvmField
   val fieldDepthSetting: Setting<Boolean>
   @JvmField
   val strengthSetting: Setting<Float>
   private var depthRenderTarget: RenderTarget? = null
   private var previousBrightness: BrightnessChoice? = null
   private var savedGamma: Double = 0.0
   @Volatile
   private var weatherOverrideSuppressed: Boolean = false
   private var serverTimeSnapshot: ServerTimeSnapshot? = null

   override fun onEnable() {
      val options: Options = ClientAccess.collectiveMinecraft.options
      val gammaOption: OptionInstance<Double> = options.gamma()
      this.savedGamma = gammaOption.get()
      if (ClientAccess.collectiveMinecraft.level != null) {
         this.serverTimeSnapshot = ServerTimeSnapshot(
            ClientAccess.collectiveMinecraft.level!!.gameTime, ClientAccess.collectiveMinecraft.level!!.gameTime
         )
      } else {
         this.serverTimeSnapshot = null
      }
   }

   override fun onToggle() {
      if (ClientAccess.collectiveMinecraft.levelRenderer != null) {
         val brightnessOption: Setting<BrightnessChoice> = this.brightnessSetting
         if (brightnessOption.getValue() == BrightnessChoice.SCREEN) {
            ClientAccess.collectiveMinecraft.levelExtractor.allChanged()
         }
      }
   }

   override fun onDisable() {
      if (!this.isWorldUnavailable()) {
         val effectPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val nightVisionEffect = effectPlayer.getEffect(MobEffects.NIGHT_VISION)
         // Удаляем только эффект модуля (amplifier == 68); настоящее ночное зрение не трогаем.
         if (nightVisionEffect != null && nightVisionEffect.amplifier == getNightVisionAmplifier()) {
            effectPlayer.removeEffect(MobEffects.NIGHT_VISION)
         }
      }

      val snapshot = this.serverTimeSnapshot
      if (snapshot != null && ClientAccess.collectiveMinecraft.level != null) {
         ClientAccess.collectiveMinecraft.level!!.setTimeFromServer(
            snapshot.gameTime
         )
      }

      val options: Options = ClientAccess.collectiveMinecraft.options
      val gammaOption: OptionInstance<Double> = options.gamma()
      val gammaToRestore = this.savedGamma
      GameOptionsOverrides.forceSetOptionValue(gammaOption, gammaToRestore)
   }

   fun updateServerTimeSnapshot(gameTime: Long, dayTime: Long) {
      this.serverTimeSnapshot = ServerTimeSnapshot(gameTime, dayTime)
   }

   fun getWorldTime(): Long {
      val syncOption: Setting<Boolean> = this.syncSetting
      if (syncOption.getValue()) {
         val calendar = Calendar.getInstance()

         var timeTicks = calendar.get(11).toFloat()

         val minutes = calendar.get(12).toFloat()

         timeTicks += minutes / 6e+01f

         timeTicks *= 1e+03f

         return (timeTicks + 1.8e+04f).toLong()
      } else {
         var configuredTimeTicks = this.timeSetting.getValue()

         configuredTimeTicks *= 1e+03f

         return (configuredTimeTicks + 1.8e+04f).toLong()
      }
   }

   fun getDepthRenderTarget(): RenderTarget? {
      return this.depthRenderTarget
   }

   fun isWeatherOverrideSuppressed(): Boolean {
      return this.weatherOverrideSuppressed
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      if (this.isWorldUnavailable()) {
         return
      }
      val worldTimeOption: Setting<Boolean> = this.worldTimeSetting
      if (worldTimeOption.getValue()) {

         val worldTime = this.getWorldTime()
         val dimensionType =
            ClientAccess.collectiveMinecraft.level!!.dimensionType() as DimensionTypeAccessor?
         ClientAccess.collectiveMinecraft.level!!.setTimeFromServer(worldTime)
         if (dimensionType != null) {
            dimensionType.`collective$setFixedTime`(false)
         }
      }

      val gammaBrightnessOption: Setting<BrightnessChoice> = this.brightnessSetting
      if (gammaBrightnessOption.getValue() == BrightnessChoice.GAMMA) {
         val options: Options = ClientAccess.collectiveMinecraft.options
         val gammaOption: OptionInstance<Double> = options.gamma()
         GameOptionsOverrides.forceSetOptionValue(gammaOption, 1000.0)
      } else {
         val potionBrightnessOption: Setting<BrightnessChoice> = this.brightnessSetting
         if (potionBrightnessOption.getValue() == BrightnessChoice.POTION) {
            val playerToCheck: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val effectToCheck = MobEffects.NIGHT_VISION
            if (!playerToCheck.hasEffect(effectToCheck)) {
               val effectPlayerCapture: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
               val nightVisionEffect = MobEffectInstance(
                  MobEffects.NIGHT_VISION, -1, getNightVisionAmplifier()
               )
               effectPlayerCapture.addEffect(nightVisionEffect)
            }
         }
      }
   }

   @EventHandler(priority = 10)
   private fun onBeforeDepthClear(event: WorldRenderEvent.BeforeDepthClear) {
      val depthOption: Setting<Boolean> = this.fieldDepthSetting
      if (depthOption.getValue()) {
         this.depthRenderTarget = ClientAccess.collectiveMinecraft.gameRenderer.mainRenderTarget()
         val renderUtils = RenderUtils.INSTANCE
         renderUtils.fillRect(
            event.poseStack!!, 0.0f, 0.0f, 0.0f, 0.0f, -1
         )
         val registry: ShaderRegistry = ClientContext.shaderRegistry
         val shaderName = "dof"
         val depthPass: ShaderPass = registry.getPass(shaderName)
         val depthDraw = Runnable { }
         depthPass.render(depthDraw)
      }
   }

   fun withWeatherOverrideSuppressed(supplier: Supplier<*>): Any? {
      this.weatherOverrideSuppressed = true
      try {
         return supplier.get()
      } finally {
         this.weatherOverrideSuppressed = false
      }
   }

   init {
      this.brightnessSetting = this.add(EnumSetting<BrightnessChoice>("Brightness", BrightnessChoice.GAMMA))
      val screenColorVisibility = Predicate<Any> { ignored -> this.brightnessSetting.getValue() == BrightnessChoice.SCREEN }
      this.colorSetting = this.add(ColorSetting("Color", Color(-1, true), screenColorVisibility))
      val occlusionVisibility = Predicate<Any> { ignored -> this.brightnessSetting.getValue().supportsOcclusion() }
      this.occlusionSetting = this.add(BooleanSetting("Occlusion", false, occlusionVisibility).markVisibilityDependency())
      this.occlusionColorSetting = this.add(ColorSetting("Color", Color(-1, true)).dependsOn(this.occlusionSetting))
      this.worldTimeSetting = this.add(BooleanSetting("WorldTime", false).markVisibilityDependency())
      this.syncSetting = this.add(BooleanSetting("Sync", false).dependsOn(this.worldTimeSetting))
      val timeVisibility = Predicate<Any> { ignored -> this.worldTimeSetting.isExpanded() && !this.syncSetting.getValue() }
      this.timeSetting = this.add(NumberSetting<Float>("Time", 0.0f, 0.0f, 24.0f, timeVisibility).withUnit("h"))
      this.worldWeatherSetting = this.add(BooleanSetting("WorldWeather", false).markVisibilityDependency())
      this.weatherSetting = this.add(EnumSetting<WeatherChoice>("Weather", WeatherChoice.CLEAR).dependsOn(this.worldWeatherSetting))
      val alwaysVisibility = Predicate<Any> { ignored -> this.weatherSetting.getValue().supportsPrecipitation() }
      this.weatherAlwaysSetting = this.add(BooleanSetting("Always", false, alwaysVisibility).dependsOn(this.worldWeatherSetting))
      this.opacitySetting = this.add(NumberSetting<Int>("Opacity", 100, 0, 100).withUnit("%").dependsOn(this.worldWeatherSetting))
      this.fieldDepthSetting = this.add(BooleanSetting("FieldDepth", false).markVisibilityDependency())
      this.strengthSetting = this.add(NumberSetting<Float>("Strength", 10.0f, 1.0f, 20.0f).dependsOn(this.fieldDepthSetting))
      val brightnessForCallback: Setting<BrightnessChoice> = this.brightnessSetting
      val brightnessChangeCallback = Runnable {
         val previousBrightnessCapture = this.previousBrightness
         val changedBrightnessOption: Setting<BrightnessChoice> = this.brightnessSetting
         if (previousBrightnessCapture != changedBrightnessOption.getValue() &&
         this.previousBrightness ==
         BrightnessChoice.GAMMA
         ) {
            val restoreGammaOption: OptionInstance<Double> = ClientAccess.collectiveMinecraft.options.gamma()
            val gammaToRestore = this.savedGamma
            GameOptionsOverrides.forceSetOptionValue(restoreGammaOption, gammaToRestore)
         } else {
            val captureGammaOption: OptionInstance<Double> = ClientAccess.collectiveMinecraft.options.gamma()
            this.savedGamma = captureGammaOption.get()
         }

         this.previousBrightness = this.brightnessSetting.getValue()
         if (!this.isWorldUnavailable()) {
            val playerToCheck: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val effectToCheck = MobEffects.NIGHT_VISION
            if (playerToCheck.hasEffect(effectToCheck)) {
               val playerForEffect: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
               val effectToGet = MobEffects.NIGHT_VISION
               val nightVisionEffect = playerForEffect.getEffect(effectToGet)
               val effectAmplifier = nightVisionEffect!!.amplifier

               val expectedAmplifier = 68
               if (effectAmplifier == expectedAmplifier) {
                  val playerForRemoval: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
                  val effectToRemove = MobEffects.NIGHT_VISION
                  playerForRemoval.removeEffect(effectToRemove)
               }
            }
         }
      }
      brightnessForCallback.setChangeCallback(brightnessChangeCallback)
      val worldTimeForCallback: Setting<Boolean> = this.worldTimeSetting
      val worldTimeChangeCallback = Runnable {
         val worldTimeOption: Setting<Boolean> = this.worldTimeSetting
         if (worldTimeOption.getValue()) {
            return@Runnable
         }
         val snapshot = this.serverTimeSnapshot
         if (snapshot != null && ClientAccess.collectiveMinecraft.level != null) {
            ClientAccess.collectiveMinecraft.level!!.setTimeFromServer(
               snapshot.gameTime
            )
            this.serverTimeSnapshot = null
         }
      }
      worldTimeForCallback.setChangeCallback(worldTimeChangeCallback)
      this.previousBrightness = this.brightnessSetting.getValue()
      this.occlusionColorSetting.setConfigName("OcclusionColor")

      this.setDrawn(false)
   }

   private data class ServerTimeSnapshot(val gameTime: Long, val dayTime: Long) {
   }

   enum class BrightnessChoice(private val displayName: String) : NamedEntry {
      SCREEN("Screen"),
      GAMMA("Gamma"),
      POTION("Potion"),
      NONE("None");

      override fun getName(): String {
         return this.displayName
      }

      fun supportsOcclusion(): Boolean {
         return this == NONE || this == POTION
      }
   }

   enum class WeatherChoice(private val displayName: String, private val precipitation: Precipitation?) : NamedEntry {
      VANILLA("Vanilla", null),
      CLEAR("Clear", Precipitation.NONE),
      SNOW("Snow", Precipitation.SNOW),
      RAIN("Rain", Precipitation.RAIN);

      fun getPrecipitation(): Precipitation? {
         return this.precipitation
      }

      fun supportsPrecipitation(): Boolean {
         return this.getPrecipitation() != null && this.getPrecipitation() != Precipitation.NONE
      }

      override fun getName(): String {
         return this.displayName
      }
   }

   companion object {
      private fun getNightVisionAmplifier(): Int {
         return 68
      }
   }
}
