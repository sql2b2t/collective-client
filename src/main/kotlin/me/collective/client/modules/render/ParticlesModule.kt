package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.ParticleEngineAddEvent
import me.collective.client.render.ColorUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.render.ParticleInitialAlpha
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.ColorsModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import java.awt.Color
import java.util.Random
import java.util.concurrent.ThreadLocalRandom
import net.minecraft.client.particle.AshParticle
import net.minecraft.client.particle.BaseAshSmokeParticle
import net.minecraft.client.particle.CritParticle
import net.minecraft.client.particle.FireworkParticles.SparkParticle
import net.minecraft.client.particle.FireworkParticles.Starter
import net.minecraft.client.particle.PortalParticle
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.client.particle.TotemParticle
import net.minecraft.client.particle.WhiteAshParticle
import net.minecraft.client.player.LocalPlayer

class ParticlesModule : Module("Particles", "Allows you to adjust certain particles.", ModuleCategory.RENDER) {
   private val random: Random = Random()
   val totemsSetting: Setting<Boolean>
   val totemsScaleSetting: Setting<Float>
   val totemsVelocitySetting: Setting<Float>
   val totemsColor1Setting: Setting<Color>
   val totemsColor2Setting: Setting<Color>
   val ignoreSelfSetting: Setting<Boolean>
   val rocketsSetting: Setting<Boolean>
   val rocketScaleSetting: Setting<Float>
   val rocketColorSetting: Setting<Color>
   val damageSetting: Setting<Boolean>
   val damageScaleSetting: Setting<Float>
   val damageVelocitySetting: Setting<Float>
   val damageColorSetting: Setting<Color>
   val portalSetting: Setting<Boolean>
   val portalScaleSetting: Setting<Float>
   val portalColorSetting: Setting<Color>
   val dustSetting: Setting<Boolean>
   val fromSetting: Setting<Color>
   val toSetting: Setting<Color>

   private fun setParticleColor(particle: SingleQuadParticle, argb: Int) {
      val alpha: Float = ((argb ushr 24) and 0xFF) / 255.0F
      val red: Float = ((argb ushr 16) and 0xFF) / 255.0F
      val green: Float = ((argb ushr 8) and 0xFF) / 255.0F
      val blue: Float = (argb and 0xFF) / 255.0F

      particle.setColor(red, green, blue)
      (particle as ParticleInitialAlpha).`collective$setInitialAlpha`(alpha)
   }

   @EventHandler
   private fun onParticleEngineAddEvent(event: ParticleEngineAddEvent) {
      if (event.particle is TotemParticle) {
         val totem: TotemParticle = event.particle as TotemParticle
         if (this.ignoreSelfSetting.getValue()) {
            val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            if (player.getBoundingBox().intersects(totem.getBoundingBox())) {
               event.cancel()
            }
         }

         if (!this.totemsSetting.getValue()) {
            return
         }

         val colorSetting: Setting<Color> = if (this.random.nextInt(4) == 0) {
            this.totemsColor1Setting
         } else {
            this.totemsColor2Setting
         }
         this.setParticleColor(totem, this.getParticleColor(colorSetting))
         totem.scale(this.totemsScaleSetting.getValue())
         totem.setPower(this.totemsVelocitySetting.getValue())
      }

      if (this.rocketsSetting.getValue()) {
         if (event.particle is Starter) {
            val starter: Starter = event.particle as Starter
            starter.scale(this.rocketScaleSetting.getValue())
         }
         if (event.particle is SparkParticle) {
            val spark: SparkParticle = event.particle as SparkParticle
            this.setParticleColor(spark, this.getParticleColor(this.rocketColorSetting))
            spark.scale(this.rocketScaleSetting.getValue())
         }
      }

      if (this.damageSetting.getValue() &&
         event.particle is CritParticle
      ) {
         val damageParticle: CritParticle = event.particle as CritParticle
         this.setParticleColor(damageParticle, this.damageColorSetting.getValue().getRGB())
         damageParticle.scale(this.damageScaleSetting.getValue())
         damageParticle.setPower(this.damageVelocitySetting.getValue())
      }

      if (this.portalSetting.getValue() &&
         event.particle is PortalParticle
      ) {
         val portalParticle: PortalParticle = event.particle as PortalParticle
         this.setParticleColor(portalParticle, this.portalColorSetting.getValue().getRGB())
         portalParticle.scale(this.portalScaleSetting.getValue())
      }

      if (this.dustSetting.getValue() &&
         event.particle is BaseAshSmokeParticle
      ) {
         val dustParticle: BaseAshSmokeParticle = event.particle as BaseAshSmokeParticle
         if (dustParticle is WhiteAshParticle) {
            val whiteAshColor: Color = this.toSetting.getValue().brighter()
            this.setParticleColor(dustParticle, whiteAshColor.getRGB())
         }
         if (dustParticle is AshParticle) {
            val from: Color = this.fromSetting.getValue()
            val to: Color = this.toSetting.getValue()
            val blended: Color = ColorUtils.blend(from, to, ThreadLocalRandom.current().nextFloat())
            this.setParticleColor(dustParticle, blended.getRGB())
         }
      }
   }

   private fun getParticleColor(setting: Setting<Color>): Int {
      val color: Color = setting.getValue()
      val colorSetting: ColorSetting = setting as ColorSetting
      if (!colorSetting.isRainbow) {
         return color.getRGB()
      }

      val hsb: FloatArray = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null)
      var period: Int = (MathConstants.FULL_TURN_DEGREES * colors.getRainbowPeriod()).toInt()
      period = Math.max(period, 1)
      val offset: Int = this.random.nextInt(period)
      val rainbow: Color = ColorUtils.rainbow(period, offset, hsb[1], hsb[2], color.getAlpha())
      return rainbow.getRGB()
   }

   init {
      this.totemsSetting = this.add(BooleanSetting("Totems", false).markVisibilityDependency())
      this.totemsScaleSetting = this.add(NumberSetting<Float>("TotemsScale", 1.0F, 0.0F, 2.0F).dependsOn(this.totemsSetting))
      this.totemsVelocitySetting = this.add(NumberSetting<Float>("TotemsVelocity", 1.0F, 0.0F, 5.0F).dependsOn(this.totemsSetting))
      this.totemsColor1Setting = this.add(ColorSetting("TotemsColor1", Color(-4351489, true)).dependsOn(this.totemsSetting))
      this.totemsColor2Setting = this.add(ColorSetting("TotemsColor2", Color(-9286761, true)).dependsOn(this.totemsSetting))
      this.ignoreSelfSetting = this.add(BooleanSetting("IgnoreSelf", false).dependsOn(this.totemsSetting))
      this.rocketsSetting = this.add(BooleanSetting("Rockets", false).markVisibilityDependency())
      this.rocketScaleSetting = this.add(NumberSetting<Float>("RocketScale", 1.0F, 0.0F, 2.0F).dependsOn(this.rocketsSetting))
      this.rocketColorSetting = this.add(ColorSetting("RocketColor", Color(-4351489, true)).dependsOn(this.rocketsSetting))
      this.damageSetting = this.add(BooleanSetting("Damage", false).markVisibilityDependency())
      this.damageScaleSetting = this.add(NumberSetting<Float>("DamageScale", 1.0F, 0.0F, 2.0F).dependsOn(this.damageSetting))
      this.damageVelocitySetting = this.add(NumberSetting<Float>("DamageVelocity", 1.0F, 0.0F, 5.0F).dependsOn(this.damageSetting))
      this.damageColorSetting = this.add(ColorSetting("DamageColor", Color(-4351489, true)).dependsOn(this.damageSetting))
      this.portalSetting = this.add(BooleanSetting("Portal", false).markVisibilityDependency())
      this.portalScaleSetting = this.add(NumberSetting<Float>("PortalScale", 1.0F, 0.0F, 2.0F).dependsOn(this.portalSetting))
      this.portalColorSetting = this.add(ColorSetting("PortalColor", Color(-4351489, true)).dependsOn(this.portalSetting))
      this.dustSetting = this.add(BooleanSetting("Dust", false).markVisibilityDependency())
      this.fromSetting = this.add(ColorSetting("From", Color(-4351489, true)).dependsOn(this.dustSetting))
      this.toSetting = this.add(ColorSetting("To", Color(-4351489, true)).dependsOn(this.dustSetting))

      this.setDrawn(false)
   }

   companion object {

      private lateinit var colors: ColorsModule

      @JvmStatic
      fun bindColorsModule(registry: ModuleRegistry) {
         colors = registry.getModule(
            ColorsModule::class.java
         ) as ColorsModule
      }
   }

}
