package me.collective.client.modules.render


import me.collective.client.ClientAccess
import me.collective.mixin.ClientLevelAccessor
import me.collective.client.utils.world.WorldUtils
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.render.ClientRenderTypes
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.DimensionType

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexFormat.Mode
import java.awt.Color
import java.util.function.Predicate
import net.minecraft.client.renderer.MappableRingBuffer
import net.minecraft.network.protocol.game.ClientboundLoginPacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.world.attribute.EnvironmentAttributeSystem
import net.minecraft.client.renderer.fog.FogRenderer
import net.minecraft.client.renderer.rendertype.RenderType
import kotlin.concurrent.Volatile

class SkyboxModule : Module("Skybox", "Changes vanilla sky rendering.", ModuleCategory.RENDER) {
   @JvmField
   val typeSetting: Setting<SkyType> = add(EnumSetting("Type", SkyType.NONE))

   @JvmField
   val splitFogSetting: Setting<Boolean> = add(BooleanSetting("SplitFog", false))

   @JvmField
   val fancyStarsSetting: Setting<Boolean> = add(BooleanSetting("FancyStars", false).markVisibilityDependency())

   @JvmField
   val starsColorSetting: Setting<Color> = add(ColorSetting("Color", Color(-1, true)).dependsOn(this.fancyStarsSetting))

   @JvmField
   val shaderSetting: Setting<Boolean> = add(BooleanSetting("Shader", false).markVisibilityDependency())

   @JvmField
   val parallaxSetting: Setting<Float> = add(NumberSetting("Parallax", 1.0F, 0.0F, 1.5F).dependsOn(this.shaderSetting))

   @JvmField
   val colorSetting: Setting<Color> = add(ColorSetting("Color", Color(-2900225, true)).dependsOn(this.shaderSetting))

   @JvmField
   val cloudsSetting: Setting<Boolean> = add(BooleanSetting("Clouds", false).markVisibilityDependency())

   @JvmField
   val heightSetting: Setting<Float> = add(NumberSetting("Height", 192.0F, 0.0F, 256.0F).dependsOn(this.cloudsSetting))

   @JvmField
   val cloudsColorSetting: Setting<Color> = add(ColorSetting("CloudsColor", Color(-1, true)).dependsOn(this.cloudsSetting))

   @JvmField
   val skyColorSetting: Setting<Boolean> = add(BooleanSetting("SkyColor", false).markAsGroup().markVisibilityDependency())

   @JvmField
   val denseSetting: Setting<Boolean> = add(BooleanSetting("Dense", false).dependsOn(this.skyColorSetting))

   @JvmField
   val fogSetting: Setting<Color> = add(ColorSetting("Fog", Color(-4351489, true)).dependsOn(this.skyColorSetting))

   @JvmField
   val skySetting: Setting<Color> = add(ColorSetting("Sky", Color(-4351489, true)).dependsOn(this.skyColorSetting))

   @JvmField
   val backgroundSetting: Setting<Color> = add(ColorSetting("Background", Color(-2900225, true)).dependsOn(this.skyColorSetting))

   private val dimensionsSetting: Setting<Boolean> = add(BooleanSetting("Dimensions", true).markAsGroup().markVisibilityDependency())

   private val overworldSetting: Setting<Boolean> = add(BooleanSetting("Overworld", true).dependsOn(this.dimensionsSetting))

   private val netherSetting: Setting<Boolean> = add(BooleanSetting("Nether", true).dependsOn(this.dimensionsSetting))

   private val endSetting: Setting<Boolean> = add(BooleanSetting("End", true).dependsOn(this.dimensionsSetting))

   private var skyFogBuffer: MappableRingBuffer? = null

   @Volatile
   private var environmentRefreshPending: Boolean = false

   override fun onToggle() {
      this.requestEnvironmentRefresh()
   }

   @EventHandler
   private fun onPacketReceive(event: PacketEvent.Receive) {
      if (event.packet is ClientboundRespawnPacket || event.packet is ClientboundLoginPacket) {
         this.requestEnvironmentRefresh()
      }
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      if (!this.environmentRefreshPending) {
         return
      }

      val level = ClientAccess.collectiveMinecraft.level!! as ClientLevelAccessor
      level.`collective$setEnvironmentAttributes`(
         level.`collective$addEnvironmentAttributeLayers`(
            EnvironmentAttributeSystem.builder().addDefaultLayers(ClientAccess.collectiveMinecraft.level!!)
         ).build()
      )
      this.environmentRefreshPending = false
   }

   private fun requestEnvironmentRefresh() {
      this.environmentRefreshPending = true
   }

   fun getCloudHeight(originalHeight: Float?): Float? {
      if (this.isToggled()) {
         if (this.cloudsSetting.value!!) {
            return this.heightSetting.value
         }
      }

      return originalHeight
   }

   fun drawShaderQuad() {
      val tesselatorCapture = Tesselator.getInstance()
      val positionFormat = DefaultVertexFormat.POSITION
      val quadMode = Mode.QUADS
      val buffer: BufferBuilder = tesselatorCapture.begin(quadMode, positionFormat)

      buffer.addVertex(-1.0F, -1.0F, 0.0F)

      buffer.addVertex(-1.0F, 1.0F, 0.0F)

      buffer.addVertex(1.0F, 1.0F, 0.0F)

      buffer.addVertex(1.0F, -1.0F, 0.0F)
      val renderTypeCapture = ClientRenderTypes.entityShader()
      val mesh: MeshData = buffer.buildOrThrow()
      renderTypeCapture.draw(mesh)
   }

   fun isCurrentDimensionEnabled(): Boolean {
      return when (WorldUtils.getDimension()) {
         DimensionType.OVERWORLD -> this.overworldSetting.value!!
         DimensionType.THE_NETHER -> this.netherSetting.value!!
         else -> this.endSetting.value!!
      }
   }

   fun getParallax(): Float {
      return this.parallaxSetting.value!!
   }

   fun getSkyFogBuffer(): MappableRingBuffer {
      if (this.skyFogBuffer == null) {
         this.skyFogBuffer = MappableRingBuffer(
            { "Sky Fog UBO" }, skyFogBufferUsage(), FogRenderer.FOG_UBO_SIZE
         )
      }

      return this.skyFogBuffer!!
   }

   fun getShaderColor(): Color {
      return this.colorSetting.value!!
   }

   fun getSkySlice(): GpuBufferSlice {

      val fogBuffer = this.getSkyFogBuffer()
      val bufferCapture = fogBuffer.currentBuffer()
      val sliceLength: Long = FogRenderer.FOG_UBO_SIZE.toLong()
      return bufferCapture.slice(0L, sliceLength)
   }

   init {
      this.setDrawn(false)
      this.typeSetting.setChangeCallback { this.requestEnvironmentRefresh() }
      this.starsColorSetting.setConfigName("StarsColor")

      this.skySetting.addVisibilityPredicate(Predicate { ignoredValue ->
         val typeSettingSnapshot = this.typeSetting
         typeSettingSnapshot.value != SkyType.END
      })

      this.denseSetting.addVisibilityPredicate(Predicate { ignoredValue ->
         val typeSettingSnapshot = this.typeSetting
         if (typeSettingSnapshot.value != SkyType.FLAT) {
            if (!this.netherSetting.value!!) {
               if (!this.endSetting.value!!) {
                  return@Predicate false
               }
            }

            return@Predicate this.typeSetting.value == SkyType.NONE
         } else {
            return@Predicate true
         }
      })
   }

   data class FogDistances(
      @get:JvmName("environmentalStart")
      val environmentalStart: Float,
      @get:JvmName("environmentalEnd")
      val environmentalEnd: Float,
      @get:JvmName("renderDistanceStart")
      val renderDistanceStart: Float,
      @get:JvmName("renderDistanceEnd")
      val renderDistanceEnd: Float,
      @get:JvmName("skyEnd")
      val skyEnd: Float,
      @get:JvmName("cloudsEnd")
      val cloudsEnd: Float
   ) {

   }

   enum class SkyType(private val displayName: String) : NamedEntry {
      NONE("Vanilla"),
      OVERWORLD("Overworld"),
      FLAT("Flat"),
      END("End");

      override fun getName(): String {
         return this.displayName
      }
   }

   companion object {
      private fun skyFogBufferUsage(): Int {
         return GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_MAP_WRITE
      }
   }
}
