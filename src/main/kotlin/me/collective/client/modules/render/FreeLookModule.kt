package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ScreenChangeEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.MouseHandlerOnScrollEvent
import me.collective.client.utils.InterpolatedFloat
import me.collective.client.utils.movement.EntityMotionAccess
import me.collective.client.render.RenderUtils
import me.collective.client.render.GameOptionsOverrides
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.player.FreecamModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting

import net.minecraft.client.Camera
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.Options
import net.minecraft.client.gui.screens.LevelLoadingScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import org.joml.Vector2f

class FreeLookModule : Module("FreeLook", "Allows you to rotate your 3rd person camera without rotating your player.", ModuleCategory.RENDER) {

   private val cameraDistanceAnimation: InterpolatedFloat
   private val cameraRotation: Vector2f
   private var cameraType: CameraType? = null
   private var cameraDistance: Float = 0.0F
   val scrollSetting: Setting<Boolean>

   override fun onEnable() {
      if (this.isWorldUnavailable()) {
         this.disable()
         return
      }

      this.cameraDistance = 1e+02F
      this.cameraDistanceAnimation.setValue(0.0F)
      val options: Options = ClientAccess.collectiveMinecraft.options
      val selectedCameraType: CameraType = if (options.getCameraType() === CameraType.FIRST_PERSON) {
         CameraType.THIRD_PERSON_BACK
      } else {
         ClientAccess.collectiveMinecraft.options.getCameraType()
      }
      this.cameraType = selectedCameraType
      GameOptionsOverrides.registerCameraOverride(this)
      val rotation = this.cameraRotation
      val yaw: Float = ClientAccess.collectiveMinecraft.player!!.getYRot()
      val pitch: Float = ClientAccess.collectiveMinecraft.player!!.getXRot()
      rotation.set(yaw, pitch)
      if (ClientAccess.collectiveMinecraft.player!! is EntityMotionAccess) {
         val cameraPlayer: EntityMotionAccess = ClientAccess.collectiveMinecraft.player!! as EntityMotionAccess
         val cameraYaw: Float = this.cameraRotation.x
         cameraPlayer.setCameraYaw(cameraYaw)
         val cameraPitch: Float = this.cameraRotation.y
         cameraPlayer.setCameraPitch(cameraPitch)
      }
   }

   override fun onDisable() {
      GameOptionsOverrides.unregisterCameraOverride(this)
      // Мир может быть недоступен (выход из мира / загрузка) — раньше здесь был player!! и NPE.
      val yawPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      val yawRotation = this.cameraRotation
      val yaw: Float = yawRotation.x()
      yawPlayer.setYRot(yaw)
      val previousYawBobPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val yawBobPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val previousYawPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val previousYawRotation = this.cameraRotation
      previousYawPlayer.yRotO = previousYawRotation.x()
      yawBobPlayer.yBob = previousYawPlayer.yRotO
      previousYawBobPlayer.yBobO = yawBobPlayer.yBob
      val pitchPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val pitchRotation = this.cameraRotation
      val pitch: Float = pitchRotation.y()
      pitchPlayer.setXRot(pitch)
      val previousPitchBobPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val pitchBobPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val previousPitchPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val previousPitchRotation = this.cameraRotation
      previousPitchPlayer.xRotO = previousPitchRotation.y()
      pitchBobPlayer.xBob = previousPitchPlayer.xRotO
      previousPitchBobPlayer.xBobO = pitchBobPlayer.xBob
      val camera: Camera = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera()
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return
      val cameraPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      camera.setLevel(level)
      camera.setEntity(cameraPlayer)
      camera.update(net.minecraft.client.DeltaTracker.ONE)
   }

   fun getCameraDistanceOffset(): Float {
      val offset: Float = this.cameraDistanceAnimation.getValue()
      return offset / 1e+01F
   }

   fun isScrollEnabled(): Boolean {
      return this.isToggled() && this.scrollSetting.getValue()
   }

   @EventHandler(priority = -200)
   private fun onLocalPlayerPreTick(event: LocalPlayerPreTickEvent) {
      val freecam = freecamModule
      if (freecam.isToggled()) {
         return
      }
      val options: Options = ClientAccess.collectiveMinecraft.options
      val selectedCameraType: CameraType = this.cameraType!!
      options.setCameraType(selectedCameraType)
      val minecraft: Minecraft = ClientAccess.collectiveMinecraft
      val pickPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val range: Double = ClientAccess.collectiveMinecraft.player!!.blockInteractionRange()
      val partialTick: Float = RenderUtils.getPartialTick()
      minecraft.hitResult = pickPlayer.pick(range, partialTick, false)
   }

   @EventHandler
   private fun onMouseHandlerOnScrollEvent(event: MouseHandlerOnScrollEvent) {
      val enabled: Boolean = this.scrollSetting.getValue()
      if (enabled) {
         val previousDistance: Float = this.cameraDistance
         val scrollDelta: Float = event.verticalOffset.toFloat() * this.cameraDistance
         this.cameraDistance = previousDistance - scrollDelta * 0.08F
         val distance: Float = this.cameraDistance
         this.cameraDistance = Mth.clamp(distance, 1.0F, 2e+02F)
         event.cancel()
      }
   }

   @EventHandler
   private fun onMinecraftSetScreenEvent(
      event: ScreenChangeEvent.Before
   ) {
      if (event.newScreen is LevelLoadingScreen) {
         this.disable()
      }
   }

   @EventHandler(priority = 200)
   private fun onMinecraftRunTickEvent(event: MinecraftRunTickEvent) {
      if (this.isWorldUnavailable()) {
         return
      }
      val targetAnimation = this.cameraDistanceAnimation
      val distance: Float = this.cameraDistance
      val target: Float = distance - 1e+02F
      targetAnimation.setTarget(target)
      this.cameraDistanceAnimation.updateAndGet()
   }

   fun getCameraRotation(): Vector2f {
      return this.cameraRotation
   }

   init {
      this.cameraDistanceAnimation = InterpolatedFloat(2.0F, true)
      this.cameraRotation = Vector2f()
      val scrollDefault: Boolean = false
      val scrollName: String = "Scroll"
      val scroll: BooleanSetting = BooleanSetting(scrollName, scrollDefault)
      this.scrollSetting = this.add(scroll)
   }

   companion object {

      private lateinit var freecamModule: FreecamModule

      @JvmStatic
      fun bindReferencedModules(registry: ModuleRegistry) {
         freecamModule = registry.getModule(
            FreecamModule::class.java
         ) as FreecamModule
      }
   }

}
