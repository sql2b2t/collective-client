package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.CameraSetupEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.ScreenChangeEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.KeyboardInputTickEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.MouseHandlerOnScrollEvent
import me.collective.client.events.MouseHandlerTurnPlayerEvent
import me.collective.client.events.GameModeUseItemPacketEvent
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.InterpolatedFloat
import me.collective.client.managers.RotationManager
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.network.PacketSender
import me.collective.client.render.GameOptionsOverrides
import me.collective.client.events.EventStage
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import net.minecraft.client.Camera
import net.minecraft.client.CameraType
import net.minecraft.client.Options
import net.minecraft.client.gui.screens.LevelLoadingScreen
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.core.Direction.Axis
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BucketItem
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

class FreecamModule : Module("Freecam", "Allows your camera to fly through walls.", ModuleCategory.PLAYER) {
   private val freecamInput: FreecamInput = FreecamInput()
   private val horizontalSpeedSetting: Setting<Float>
   private val verticalSpeedSetting: Setting<Float>
   private val scrollSpeedSetting: Setting<Boolean>
   @JvmField
   val noHandsSetting: Setting<Boolean>
   private val rotatePlayerSetting: Setting<Boolean>
   private val alwaysRotateSetting: Setting<Boolean>
   private val clampCameraYSetting: Setting<Boolean>
   private val minimumYSetting: Setting<Float>
   private val replacementYSetting: Setting<Float>
   private val maximumYSetting: Setting<Float>
   @JvmField
   var cameraPosition: Vec3? = null
   @JvmField
   var previousCameraPosition: Vec3? = null
   private var savedPlayerRotation: FloatArray? = null
   @JvmField
   var cameraYaw: Float = 0.0f
   @JvmField
   var previousCameraYaw: Float = 0.0f
   @JvmField
   var cameraPitch: Float = 0.0f
   @JvmField
   var previousCameraPitch: Float = 0.0f
   private val horizontalSpeedAnimation: InterpolatedFloat = InterpolatedFloat(2.0f, true)
   private val verticalSpeedAnimation: InterpolatedFloat = InterpolatedFloat(2.0f, true)

   override fun onEnable() {
      var animation: InterpolatedFloat = this.horizontalSpeedAnimation
      var targetSpeed: Float = this.horizontalSpeedSetting.getValue()!!

      animation.setValue(targetSpeed)
      animation = this.verticalSpeedAnimation
      targetSpeed = this.verticalSpeedSetting.getValue()!!
      animation.setValue(targetSpeed)
      if (this.isWorldUnavailable()) {
         this.disable()
         return
      }

      GameOptionsOverrides.registerCameraOverride(this)
      val resetInputEvent: KeyboardInputTickEvent = KeyboardInputTickEvent(
         ClientAccess.collectiveMinecraft.player!!.input
      )
      resetInputEvent.reset()
      val gameRenderer: GameRenderer = ClientAccess.collectiveMinecraft.gameRenderer
      val camera: Camera = gameRenderer.mainCamera()
      val cameraPositionSnapshot: Vec3 = camera.position()
      this.cameraPosition = cameraPositionSnapshot
      this.previousCameraPosition = cameraPositionSnapshot

      val savedRotation: FloatArray = FloatArray(2)
      val playerForYaw: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      savedRotation[0] = playerForYaw.getYRot()
      val playerForPitch: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      savedRotation[1] = playerForPitch.getXRot()
      this.savedPlayerRotation = savedRotation
      this.cameraYaw = this.savedPlayerRotation!![0]
      this.previousCameraYaw = this.cameraYaw
      this.cameraPitch = this.savedPlayerRotation!![1]
      this.previousCameraPitch = this.cameraPitch
      val cameraY: Float = this.cameraPosition!!.y.toFloat()
      var minimumY: Float = this.minimumYSetting.getValue()!!
      var maximumY: Float = this.maximumYSetting.getValue()!!
      if (maximumY < minimumY) {
         maximumY = minimumY
         minimumY = this.maximumYSetting.getValue()!!
      }

      if ((this.clampCameraYSetting.getValue()!!) && (cameraY > maximumY || cameraY < minimumY)) {
         val positionBeforeClamp: Vec3 = this.cameraPosition!!
         val yAxis: Axis = Axis.Y
         val replacementY: Double = (this.replacementYSetting.getValue()!!).toFloat().toDouble()
         this.cameraPosition = positionBeforeClamp.with(
            yAxis, replacementY
         )
         this.previousCameraPosition = this.cameraPosition
      }
   }

   override fun onDisable() {
      if (!this.isWorldUnavailable() && this.savedPlayerRotation != null) {
         val playerForYaw: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val savedYaw: Float = this.savedPlayerRotation!![0]
         playerForYaw.setYRot(savedYaw)
         val playerForPitch: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val savedPitch: Float = this.savedPlayerRotation!![1]
         playerForPitch.setXRot(savedPitch)
      }

      // Снимаем override камеры безусловно: иначе при недоступном мире
      // запись остаётся навсегда и тип камеры (F5) не восстанавливается.
      GameOptionsOverrides.unregisterCameraOverride(this)
   }

   fun shouldHideHands(): Boolean {
      if (!this.isToggled()) {
         return false
      }
      return this.noHandsSetting.getValue()!!
   }

   fun rotateCamera(yawDelta: Double, pitchDelta: Double) {
      this.previousCameraYaw = this.cameraYaw
      this.previousCameraPitch = this.cameraPitch
      this.cameraYaw = (
         this.cameraYaw + yawDelta
         ).toFloat()
      this.cameraPitch = (
         this.cameraPitch + pitchDelta
         ).toFloat()
      val requestedPitch: Float = this.cameraPitch
      val minimumPitch: Float = -MathConstants.NINETY_DEGREES.toFloat()
      val maximumPitch: Float = MathConstants.NINETY_DEGREES.toFloat()
      this.cameraPitch = Mth.clamp(requestedPitch, minimumPitch, maximumPitch)
   }

   fun getInterpolatedX(partialTick: Float): Double {
      val capturedPartialTick: Double = partialTick.toDouble()
      val previousCoordinate: Double = this.previousCameraPosition!!.x
      val currentCoordinate: Double = this.cameraPosition!!.x
      return Mth.lerp(capturedPartialTick, previousCoordinate, currentCoordinate)
   }

   @EventHandler
   private fun onMultiPlayerGameModeUseItemOnEvent(event: MultiPlayerGameModeUseItemOnEvent) {
      val capturedPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val hand: InteractionHand = event.hand
      val holdingBucket: Boolean = capturedPlayer.getItemInHand(hand).getItem() is BucketItem
      if (!holdingBucket) {
         val rotateEnabled: Boolean = this.rotatePlayerSetting.getValue()!!
         if (!rotateEnabled) {
            return
         }

         if (!RotationManager.isMovementSyncEnabled()) {
            return
         }
      }
      val capturedHitPosition: Vec3 = event.hitResult.getLocation()

      val angles: FloatArray = RotationManager.getRotationsToPosition(capturedHitPosition)

      if (RotationManager.isSilentRotationMode()) {
         val capturedX: Double = ClientAccess.collectiveMinecraft.player!!.getX()
         val capturedY: Double = ClientAccess.collectiveMinecraft.player!!.getY()
         val capturedZ: Double = ClientAccess.collectiveMinecraft.player!!.getZ()
         val capturedYaw: Float = angles[0]
         val capturedPitch: Float = angles[1]
         val rotationFlag: Boolean = ClientContext.playerStateTracker.isPacketOnGround()
         PacketSender.sendPositionRotation(
            capturedX, capturedY, capturedZ, capturedYaw, capturedPitch, rotationFlag
         )
      } else {
         ClientContext.rotationManager.requestRotation(angles, 1000)
      }
   }

   @EventHandler
   private fun onUseItemPacket(event: GameModeUseItemPacketEvent) {
      val rotateEnabled: Boolean = this.rotatePlayerSetting.getValue()!!
      if (!rotateEnabled || ClientAccess.collectiveMinecraft.hitResult == null) {
         return
      }
      if (this.alwaysRotateSetting.getValue()!!) {
         return
      }

      val hitResult: HitResult = ClientAccess.collectiveMinecraft.hitResult!!
      val capturedHitPosition: Vec3 = hitResult.getLocation()

      val angles: FloatArray = RotationManager.getRotationsToPosition(capturedHitPosition)
      event.setRotations(angles)
   }

   @EventHandler
   private fun onKeyboardInputPre(event: KeyboardInputTickEvent.Pre) {
      event.cancel()
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      val options: Options = ClientAccess.collectiveMinecraft.options
      val cameraType: CameraType = CameraType.FIRST_PERSON
      options.setCameraType(cameraType)
      this.previousCameraPosition = this.cameraPosition
      val capturedYaw: Float = this.cameraYaw
      val capturedInput: FreecamInput = this.freecamInput
      val horizontalSpeed: Double = this.horizontalSpeedAnimation.getValue().toDouble()
      val movement: DoubleArray = MovementPhysics.getHorizontalMotion(capturedYaw, capturedInput, horizontalSpeed)
      val position: Vec3 = this.cameraPosition!!
      val capturedDeltaX: Double = movement[0]
      val deltaZ: Double = movement[1]
      this.cameraPosition = position.add(capturedDeltaX, 0.0, deltaZ)
      if (ClientAccess.collectiveMinecraft.options.keyJump.isDown) {
         val positionBeforeJump: Vec3 = this.cameraPosition!!
         val capturedUpSpeed: Double = this.verticalSpeedAnimation.getValue().toDouble()
         this.cameraPosition = positionBeforeJump.add(0.0, capturedUpSpeed, 0.0)
      }

      if (ClientAccess.collectiveMinecraft.options.keyShift.isDown) {
         val positionBeforeSneak: Vec3 = this.cameraPosition!!
         val capturedDownSpeed: Double = this.verticalSpeedAnimation.getValue().toDouble()
         this.cameraPosition = positionBeforeSneak.subtract(0.0, capturedDownSpeed, 0.0)
      }

      this.freecamInput.tick()
   }

   @EventHandler
   private fun onMouseHandlerOnScrollEvent(event: MouseHandlerOnScrollEvent) {
      if (this.scrollSpeedSetting.getValue()!!) {
         var animation: InterpolatedFloat = this.horizontalSpeedAnimation
         var scrollDelta: Float = event.verticalOffset.toFloat()
         var speedSettingSnapshot: Setting<Float> = this.horizontalSpeedSetting
         val maximumHorizontalSpeed: Float = speedSettingSnapshot.getMaximumValue()!!
         this.adjustScrollSpeed(animation, scrollDelta, maximumHorizontalSpeed)
         animation = this.verticalSpeedAnimation
         scrollDelta = event.verticalOffset.toFloat()
         speedSettingSnapshot = this.verticalSpeedSetting
         val maximumVerticalSpeed: Float = speedSettingSnapshot.getMaximumValue()!!
         this.adjustScrollSpeed(animation, scrollDelta, maximumVerticalSpeed)
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

   private fun adjustScrollSpeed(
      animation: InterpolatedFloat, scrollDelta: Float, maximumSpeed: Float
   ) {
      val currentSpeed: Float = animation.getValue()

      val speedIncrement: Float = Math.signum(scrollDelta) * 0.1f * currentSpeed
      val requestedSpeed: Float = currentSpeed + speedIncrement
      val clampedSpeed: Float = Mth.clamp(requestedSpeed, 0.0f, maximumSpeed)
      animation.setTarget(clampedSpeed)
   }

   @EventHandler
   private fun onCameraSetupEvent(event: CameraSetupEvent) {
      val partialTick: Float = ClientAccess.collectiveMinecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true)

      val position: Vec3 = Vec3(
         this.getInterpolatedX(partialTick),
         this.getInterpolatedY(partialTick),
         this.getInterpolatedZ(partialTick)
      )
      event.setPosition(position)
      event.cancel()
   }

   @EventHandler
   private fun onMouseHandlerTurnPlayerEvent(event: MouseHandlerTurnPlayerEvent) {
      var yawDelta: Double = event.yawDelta

      yawDelta *= 0.15000000596046448
      var pitchDelta: Double = event.pitchDelta

      pitchDelta *= 0.15000000596046448

      this.rotateCamera(yawDelta, pitchDelta)
      event.cancel()
   }

   fun getInterpolatedZ(partialTick: Float): Double {
      val capturedPartialTick: Double = partialTick.toDouble()
      val previousCoordinate: Double = this.previousCameraPosition!!.z
      val currentCoordinate: Double = this.cameraPosition!!.z
      return Mth.lerp(capturedPartialTick, previousCoordinate, currentCoordinate)
   }

   fun getInterpolatedY(partialTick: Float): Double {
      val capturedPartialTick: Double = partialTick.toDouble()
      val previousCoordinate: Double = this.previousCameraPosition!!.y
      val currentCoordinate: Double = this.cameraPosition!!.y
      return Mth.lerp(capturedPartialTick, previousCoordinate, currentCoordinate)
   }

   fun getInterpolatedYaw(partialTick: Float): Double {
      if (ClientAccess.collectiveMinecraft.gui.screen() != null) {
         return this.cameraYaw.toDouble()
      }

      val interpolationAmount: Float = partialTick
      val previousYaw: Float = this.previousCameraYaw
      val currentYaw: Float = this.cameraYaw
      return Mth.lerp(interpolationAmount, previousYaw, currentYaw).toDouble()
   }

   fun getInterpolatedPitch(partialTick: Float): Double {
      if (ClientAccess.collectiveMinecraft.gui.screen() != null) {
         return this.cameraPitch.toDouble()
      }

      val interpolationAmount: Float = partialTick
      val previousPitch: Float = this.previousCameraPitch
      val currentPitch: Float = this.cameraPitch
      return Mth.lerp(interpolationAmount, previousPitch, currentPitch).toDouble()
   }

   @EventHandler
   private fun onMinecraftRunTickEvent(event: MinecraftRunTickEvent) {
      this.horizontalSpeedAnimation.updateAndGet()
      this.verticalSpeedAnimation.updateAndGet()
   }

   @EventHandler
   private fun onPacketReceive(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundDisconnectPacket) {
         // Событие постится с netty-потока: disable() трогает игрока и опции,
         // поэтому переносим его на главный поток клиента.
         ClientAccess.collectiveMinecraft.execute { this.disable() }
      }
   }

   @EventHandler(priority = 1000)
   private fun onLocalPlayerSendPositionEvent(event: LocalPlayerSendPositionEvent) {
      val gameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
      val destroyingBlock: Boolean = gameMode.isDestroying()
      if (event.stage == EventStage.Post) {
         return
      }
      val rotateEnabled: Boolean = this.rotatePlayerSetting.getValue()!!
      if (!rotateEnabled || ClientAccess.collectiveMinecraft.hitResult == null) {
         return
      }
      if (RotationManager.isMovementSyncEnabled() && !destroyingBlock) {
         val alwaysRotate: Boolean = this.alwaysRotateSetting.getValue()!!
         if (!alwaysRotate) {
            return
         }
      }

      val hitResult: HitResult = ClientAccess.collectiveMinecraft.hitResult!!
      val capturedHitPosition: Vec3 = hitResult.getLocation()

      val angles: FloatArray = RotationManager.getRotationsToPosition(capturedHitPosition)
      event.setRotation(angles)
   }

   init {
      this.horizontalSpeedSetting = this.add(NumberSetting<Float>("Speed", 1.0f, 0.1f, 5.0f))
      this.verticalSpeedSetting = this.add(NumberSetting<Float>("VerticalSpeed", 0.5f, 0.1f, 5.0f))
      this.scrollSpeedSetting = this.add(BooleanSetting("Scroll", false))
      this.noHandsSetting = this.add(BooleanSetting("NoHands", true))
      val rotate: BooleanSetting = BooleanSetting("Rotate", false)
      rotate.markVisibilityDependency()
      this.rotatePlayerSetting = this.add(rotate)
      this.alwaysRotateSetting = this.add(BooleanSetting("Always", false).dependsOn(this.rotatePlayerSetting))
      val clampY: BooleanSetting = BooleanSetting("ClampY", false)
      clampY.markVisibilityDependency()
      this.clampCameraYSetting = this.add(clampY)
      this.minimumYSetting = this.add(NumberSetting<Float>("Min", -64.0f, -64.0f, 320.0f).dependsOn(this.clampCameraYSetting))
      this.replacementYSetting = this.add(NumberSetting<Float>("Value", 64.0f, -64.0f, 320.0f).dependsOn(this.clampCameraYSetting))
      this.maximumYSetting = this.add(NumberSetting<Float>("Max", 300.0f, -64.0f, 320.0f).dependsOn(this.clampCameraYSetting))
      this.savedPlayerRotation = null

      val horizontalCallbackSetting: Setting<Float> = this.horizontalSpeedSetting
      val horizontalCallback = Runnable {
         val animationToUpdate: InterpolatedFloat = this.horizontalSpeedAnimation
         val changedSetting: Setting<Float> = this.horizontalSpeedSetting
         val changedValue: Float? = changedSetting.getValue()
         val newSpeed: Float = changedValue!!

         animationToUpdate.setValue(newSpeed)
      }
      horizontalCallbackSetting.setChangeCallback(horizontalCallback)
      val verticalCallbackSetting: Setting<Float> = this.verticalSpeedSetting
      val verticalCallback = Runnable {
         val animationToUpdate: InterpolatedFloat = this.verticalSpeedAnimation
         val changedSetting: Setting<Float> = this.verticalSpeedSetting
         val changedValue: Float? = changedSetting.getValue()
         val newSpeed: Float = changedValue!!

         animationToUpdate.setValue(newSpeed)
      }
      verticalCallbackSetting.setChangeCallback(verticalCallback)
      var settingToRefresh: Setting<Float> = this.horizontalSpeedSetting
      settingToRefresh.disableNumericBounds()
      settingToRefresh = this.verticalSpeedSetting

      settingToRefresh.disableNumericBounds()
   }

}
