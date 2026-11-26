package me.collective.client.modules.movement


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.math.Vec3Utils
import me.collective.client.events.StepHeightEvent
import me.collective.client.utils.ElapsedTimer
import me.collective.client.managers.HoleManager
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.combat.RaycastUtils
import me.collective.client.events.EventStage
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.exploit.PhaseModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.mixin.BlockBehaviourAccessor
import me.collective.mixin.LocalPlayerAccessor
import java.util.HashMap
import java.util.Iterator
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction.Axis
import net.minecraft.core.Vec3i
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape

class FastFallModule : Module("FastFall", "Allows you to get in holes with ease.", ModuleCategory.MOVEMENT, "reversestep") {
   internal val modeSetting: Setting<ModeChoice>
   private val heightSetting: Setting<Float>
   private val speedSetting: Setting<Float>
   private val stopInLiquidsSetting: Setting<Boolean>
   private val onlyHoleSetting: Setting<Boolean>
   private val safeDisableSetting: Setting<Boolean>

   @JvmField
   var suppressSprint: Boolean = false
   private var strictTimerArmed: Boolean = false
   private var strictTimerPulse: Boolean = false
   private var holeBelow: Boolean = false
   private var acceleratingFall: Boolean = false
   private var hasFastFallen: Boolean = false
   private var steppedThisTick: Boolean = false
   private var collisionBlockYOffset: Double = 0.0
   private var groundDistance: Double = 0.0
   private val strictPulseTimer: ElapsedTimer

   private fun canUseStrictTimer(): Boolean {
      if (!this.canFastFall() || !this.isStrictMode() ||
         this.collisionBlockYOffset == 0.0 || this.groundDistance == 0.0) {
         return false
      }
      val currentGroundDistance: Double = this.groundDistance
      val maximumHeight: Float = this.heightSetting.value
      return currentGroundDistance <= maximumHeight.toDouble()
   }

   private fun releaseStrictTimer() {
      if (this.isStrictMode()) {
         this.strictTimerArmed = false
         ClientContext.playerTickController.releaseMultiplier(this)
      }
   }

   private fun isUnsupportedAtEdge(): Boolean {
      val bounds: AABB = ClientAccess.collectiveMinecraft.player!!.boundingBox
      val center: Vec3 = bounds.center
      val minX: Double = bounds.minX
      val minZ: Double = bounds.minZ
      val maxX: Double = bounds.maxX
      val maxZ: Double = bounds.maxZ
      val rayEnds: HashMap<Vec3, Vec3> = HashMap()
      rayEnds[center] = Vec3(center.x, center.y - 1.0, center.z)
      rayEnds[Vec3(minX, center.y, minZ)] = Vec3(minX, center.y - 1.0, minZ)
      rayEnds[Vec3(maxX, center.y, minZ)] = Vec3(maxX, center.y - 1.0, minZ)
      rayEnds[Vec3(minX, center.y, maxZ)] = Vec3(minX, center.y - 1.0, maxZ)
      rayEnds[Vec3(maxX, center.y, maxZ)] = Vec3(maxX, center.y - 1.0, maxZ)
      val rayStarts = rayEnds.keys.iterator()
      while (rayStarts.hasNext()) {
         val rayStart: Vec3 = rayStarts.next()
         val hit = ClientAccess.collectiveMinecraft.level!!.clip(
            ClipContext(
               rayStart,
               rayEnds[rayStart]!!,
               ClipContext.Block.COLLIDER,
               ClipContext.Fluid.NONE,
               ClientAccess.collectiveMinecraft.player!!
            )
         )
         if (hit != null) {
            if (hit.type == HitResult.Type.BLOCK) {
               return false
            }
         }
      }
      if (!this.hasCollidableBlockAhead()) {
         return false
      }
      val belowLevelSnapshot: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val playerX: Double = ClientAccess.collectiveMinecraft.player!!.x
      val playerY: Double = ClientAccess.collectiveMinecraft.player!!.y
      val belowPlayerY: Double = playerY - 1.0
      val belowZ: Double = ClientAccess.collectiveMinecraft.player!!.z
      val belowPos: BlockPos = BlockPos.containing(playerX, belowPlayerY, belowZ)
      val stateAtFeet = belowLevelSnapshot.getBlockState(belowPos)
      if (stateAtFeet != null) {
         if (stateAtFeet.block != Blocks.AIR) {
            return false
         }
      }
      return true
   }

   private fun hasCollidableBlockAhead(): Boolean {
      val velocity: Vec3 = ClientAccess.collectiveMinecraft.player!!.deltaMovement
      var xDirection: Double = velocity.x
      var zDirection: Double = velocity.z
      val absoluteZ: Double = Math.abs(zDirection)
      val absoluteX: Double = Math.abs(xDirection)
      xDirection = if (absoluteZ > absoluteX) 0.0 else Math.signum(xDirection)
      val projectedAbsoluteX: Double = Math.abs(xDirection)
      val originalAbsoluteZ: Double = Math.abs(zDirection)
      zDirection = if (projectedAbsoluteX > originalAbsoluteZ) 0.0 else Math.signum(zDirection)
      val truncatedY: Double = ClientAccess.collectiveMinecraft.player!!.y.toInt().toDouble()
      val fractionY: Double = ClientAccess.collectiveMinecraft.player!!.y - truncatedY
      val scanY: Double = truncatedY + Math.signum(fractionY)
      val levelSnapshot: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val aheadXSnapshot: Double = ClientAccess.collectiveMinecraft.player!!.x + xDirection
      val aheadYSnapshot: Double = scanY - 1.0
      val aheadZ: Double = ClientAccess.collectiveMinecraft.player!!.z + zDirection
      val aheadPos: BlockPos = BlockPos.containing(aheadXSnapshot, aheadYSnapshot, aheadZ)
      val aheadState = levelSnapshot.getBlockState(aheadPos)
      if (aheadState != null) {
         return (aheadState.block as BlockBehaviourAccessor).isCollidable()
      } else {
         return true
      }
   }

   private fun canFastFall(): Boolean {
      if (speedModule!!.isToggled()) {
         if (speedModule!!.modeSetting.value != SpeedModule.ModeChoice.OnGround) {
            if (speedModule!!.modeSetting.value != SpeedModule.ModeChoice.Vanilla) {
               return false
            }
         }
      }
      if (ClientAccess.collectiveMinecraft.player!!.isShiftKeyDown) {
         return false
      }
      if (ClientAccess.collectiveMinecraft.player!!.isFallFlying) {
         return false
      }
      if (ClientAccess.collectiveMinecraft.player!!.isInWall) {
         return false
      }
      if (PlayerStateUtils.isInLiquid(ClientAccess.collectiveMinecraft.player!!)) {
         if (this.stopInLiquidsSetting.value) {
            return false
         }
      }
      if (!ClientContext.serverStateManager.setbackTimer.hasElapsed(500L)) {
         return false
      }
      val levelSnapshot: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val feetPos: BlockPos = BlockPos.containing(ClientAccess.collectiveMinecraft.player!!.position())
      val feetState: BlockState = levelSnapshot.getBlockState(feetPos)
      return feetState.block !is BedBlock
   }

   override fun onToggle() {
      this.suppressSprint = false
      this.hasFastFallen = false
   }

   private fun isStrictMode(): Boolean {
      return this.modeSetting.value == ModeChoice.STRICT
   }

   @EventHandler
   private fun onMove(event: LocalPlayerMoveEvent) {
      this.collisionBlockYOffset = RaycastUtils.getCollisionBlockYOffset().toDouble()
      this.groundDistance = RaycastUtils.getGroundDistance().toDouble()
      val holeManagerSnapshot: HoleManager = ClientContext.holeManager
      val position: BlockPos = ClientAccess.collectiveMinecraft.player!!.blockPosition()
      val blocksBelow: Int = this.groundDistance.toInt()
      val targetPos: BlockPos = position.below(blocksBelow)
      this.holeBelow = holeManagerSnapshot.intersectsHole(
         targetPos
      )
      if (!this.holeBelow) {
         if (this.onlyHoleSetting.value) {
            return
         }
      }
      val horizontalFactor = 0.3
      var hasHoleSnapTarget: Boolean = false
      if (holeSnapModule!!.isToggled()) {
         if (holeSnapModule!!.targetTimeoutTimer.hasElapsed(500L)) {
            hasHoleSnapTarget = holeSnapModule!!.getSnapTarget() != null
         }
      }
      var strictGroundedFall: Boolean = false
      if (this.isStrictMode() && this.canFastFall() && this.isUnsupportedAtEdge()) {
         strictGroundedFall = ClientAccess.collectiveMinecraft.player!!.onGround()
      }
      if (!strictGroundedFall) {
         if (!this.acceleratingFall || hasHoleSnapTarget) {
            return
         }
         if (!this.hasCollidableBlockAhead()) {
            return
         }
      }
      val movementSnapshot: Vec3 = event.velocity
      val scaledXSnapshot: Double = event.x * horizontalFactor
      val eventY: Double = event.y
      val scaledZ: Double = event.z * horizontalFactor
      Vec3Utils.setComponents(movementSnapshot, scaledXSnapshot, eventY, scaledZ)
   }

   @EventHandler
   private fun onStepHeight(event: StepHeightEvent) {
      if (event.stage == EventStage.Post) {
         if (ClientAccess.collectiveMinecraft.player!!.onGround()) {
            val height: Float = event.height
            if (height > 0.6) {
               this.steppedThisTick = true
            }
         }
      }
   }

   @EventHandler
   private fun onPlayerTick(event: LocalPlayerTickEvent) {
      var movementAllowed: Boolean = MovementPhysics.isLocalPlayerMoving()
      if (!movementAllowed) {
         if (holeSnapModule!!.isToggled()) {
            movementAllowed = holeSnapModule!!.getSnapTarget() != null
         }
      }
      if (!this.holeBelow) {
         if (this.onlyHoleSetting.value) {
            return
         }
      }
      if (!movementAllowed) {
         return
      }
      if (this.steppedThisTick) {
         this.steppedThisTick = false
         return
      }
      this.acceleratingFall = false
      if (!(ClientAccess.collectiveMinecraft.player!! as LocalPlayerAccessor).lastOnGround()) {
         return
      }
      val currentVelocity: Vec3 = ClientAccess.collectiveMinecraft.player!!.deltaMovement
      if (!(currentVelocity.y <= 0.0)) {
         return
      }
      if (!this.canFastFall()) {
         return
      }
      if (this.isStrictMode()) {
         return
      }
      val playerY: Double = ClientAccess.collectiveMinecraft.player!!.y
      val boundsSnapshot: AABB = ClientAccess.collectiveMinecraft.player!!.boundingBox
      val maximumHeight: Float = this.heightSetting.value
      val negativeHeight: Double = (-maximumHeight).toDouble()
      val scanBounds: AABB = boundsSnapshot.expandTowards(0.0, negativeHeight, 0.0).setMaxY(playerY)
      if (ClientAccess.collectiveMinecraft.level!!.noCollision(ClientAccess.collectiveMinecraft.player!!, scanBounds) &&
         !ClientAccess.collectiveMinecraft.level!!.containsAnyLiquid(scanBounds)) {
         return
      }
      val collisions: Iterable<VoxelShape> = ClientAccess.collectiveMinecraft.level!!.getBlockCollisions(ClientAccess.collectiveMinecraft.player!!, scanBounds)
      val highestCollisionY = AtomicReference<Double>(
         (ClientAccess.collectiveMinecraft.level!!.minY).toDouble()
      )
      collisions.forEach { shape: VoxelShape ->
         val previousHighestSnapshot: Double = highestCollisionY.get()
         val verticalAxis: Axis = Axis.Y
         val shapeTopY: Double = shape.max(verticalAxis)
         val newHighestY: Double = Math.max(previousHighestSnapshot, shapeTopY)
         highestCollisionY.set(newHighestY)
      }
      val highestY: Double = highestCollisionY.get()
      if (!(highestY < ClientAccess.collectiveMinecraft.player!!.y)) {
         return
      }
      var limitDescent: Boolean = true
      var destinationY: Double = highestCollisionY.get()
      val velocity: Vec3 = ClientAccess.collectiveMinecraft.player!!.deltaMovement
      val destinationXSnapshot: Double = ClientAccess.collectiveMinecraft.player!!.x
      val destinationZ: Double = ClientAccess.collectiveMinecraft.player!!.z
      val destinationPos: BlockPos = BlockPos.containing(destinationXSnapshot, destinationY, destinationZ)
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val velocityOffset: Vec3i = Vec3i(velocity.x.toInt(), -1, velocity.z.toInt())
      if (!level.isEmptyBlock(destinationPos.offset(velocityOffset))) {
         if (ClientContext.serverStateManager.teleportAckTimer.hasElapsed(25L)) {
            limitDescent = this.modeSetting.value == ModeChoice.PLAIN
         }
      }
      val dropDistance: Double = ClientAccess.collectiveMinecraft.player!!.y - destinationY
      if (dropDistance > 2.0 || limitDescent) {
         val limitY: Double = ClientAccess.collectiveMinecraft.player!!.y
         destinationY = limitY - 2.0
         this.acceleratingFall = true
         val speedMode: Setting<ModeChoice> = this.modeSetting
         val selectedSpeed: Float
         if (speedMode.value == ModeChoice.PLAIN) {
            val configuredSpeed: Float = this.speedSetting.value
            selectedSpeed = configuredSpeed
         } else {
            selectedSpeed = 1.0f
         }
         if (selectedSpeed != 0.0f) {
            val velocityReceiver: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val downwardSpeed: Double = (-selectedSpeed).toDouble()
            val verticalAxis: Axis = Axis.Y
            val acceleratedVelocity: Vec3 = velocity.with(verticalAxis, downwardSpeed)
            velocityReceiver.setDeltaMovement(acceleratedVelocity)
         }
      }
      if (!limitDescent) {
         val positionTarget: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val positionXSnapshot: Double = positionTarget.x
         val positionZ: Double = ClientAccess.collectiveMinecraft.player!!.z
         positionTarget.setPos(positionXSnapshot, destinationY, positionZ)
      }
      phaseModule!!.resetGroundedTicks()
      this.hasFastFallen = true
   }

   @EventHandler
   private fun onStrictTimerTick(event: LocalPlayerPreTickEvent) {
      if (this.safeDisableSetting.value) {
         if (ClientContext.holeManager.isInHole() &&
         this.hasFastFallen) {
            this.disable()
            return
         }
      }
      if (!this.canUseStrictTimer() ||
         (!this.holeBelow && this.onlyHoleSetting.value)) {
         this.releaseStrictTimer()
         return
      }
      if (!ClientAccess.collectiveMinecraft.player!!.onGround()) {
         var pulseTimerSnapshot: ElapsedTimer = this.strictPulseTimer
         if (pulseTimerSnapshot.hasElapsed(500L)) {
            this.strictTimerPulse = !this.strictTimerPulse
            pulseTimerSnapshot = this.strictPulseTimer
            pulseTimerSnapshot.reset()
         }
         if (ClientAccess.collectiveMinecraft.player!!.deltaMovement.y < 0.0 &&
         this.strictTimerArmed &&
         this.strictTimerPulse) {
            ClientContext.playerTickController.requestMultiplier(this, 2.0f)
            this.hasFastFallen = true
         } else {
            this.releaseStrictTimer()
         }
      } else {
         this.strictTimerArmed = true
         ClientContext.playerTickController.releaseMultiplier(this)
         val groundCounter: Int = ClientContext.playerStateTracker.getGroundedTicks()
         if (groundCounter > 3) {
            this.strictPulseTimer.reset()
            this.strictTimerPulse = true
         }
      }
   }

   companion object {
      private var speedModule: SpeedModule? = null
      private var holeSnapModule: HoleSnapModule? = null
      private var phaseModule: PhaseModule? = null

      @JvmStatic
      fun bindReferencedModules(registry: ModuleRegistry) {
         speedModule = registry.getModule(SpeedModule::class.java) as SpeedModule
         holeSnapModule = registry.getModule(HoleSnapModule::class.java) as HoleSnapModule
         phaseModule = registry.getModule(PhaseModule::class.java) as PhaseModule
      }
   }

   init {
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", ModeChoice.PLAIN))
      this.heightSetting = this.add(NumberSetting<Float>("Height", 2.0f, 1.0f, 15.0f).withUnit("m"))
      val showSpeed: Predicate<Any> = Predicate { ignored -> this.modeSetting.value == ModeChoice.PLAIN }
      this.speedSetting = this.add(NumberSetting<Float>("Speed", 1.0f, 0.0f, 5.0f, showSpeed))
      this.stopInLiquidsSetting = this.add(BooleanSetting("StopInLiquids", true))
      this.onlyHoleSetting = this.add(BooleanSetting("OnlyHole", false))
      this.safeDisableSetting = this.add(BooleanSetting("SafeDisable", false))
      this.strictPulseTimer = ElapsedTimer()
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      PLAIN("Plain"),
      STRICT("Strict"),
      INSTANT("Instant");

      override fun getName(): String {
         return this.displayName
      }
   }
}
