package me.collective.client.modules.player

import java.awt.Color
import java.util.ArrayList
import java.util.HashSet
import java.util.concurrent.ThreadLocalRandom
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.KeyboardInputTickEvent
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.PlayerIsStayingOnGroundSurfaceEvent
import me.collective.client.events.RotationRequestEvent
import me.collective.client.managers.CrystalInteractionManager
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.managers.TickRateController
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.render.FeatureBoxRenderer
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.ValueProvider
import me.collective.client.settings.options.ListFilterMode
import me.collective.client.settings.options.ScaffoldSwapChoice

import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.InputUtils
import me.collective.client.utils.combat.BlockRaycastContext
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.combat.RaycastUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.mixin.BlockBehaviourAccessor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.ClientInput
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.Axis
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.attributes.AttributeInstance
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Input
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FallingBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult.Type
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class ScaffoldModule : Module("Scaffold", "Speedbridges for you automatically.", ModuleCategory.PLAYER) {
   private val modeSetting: Setting<ScaffoldMode>
   private val sizeSetting: Setting<Int>
   private val frequencySetting: Setting<Int>
   private val blocksSetting: Setting<Set<Block>>
   private val selectionSetting: Setting<ListFilterMode>
   private val swapSetting: Setting<ScaffoldSwapChoice>
   private val delaySetting: Setting<Int>
   internal val offsetSetting: Setting<Float>
   val towerSetting: Setting<TowerChoice>
   @JvmField
   val safeWalkSetting: Setting<Boolean>
   internal val airPlaceSetting: Setting<Boolean>
   internal val tellySetting: Setting<Boolean>
   @JvmField
   val andromedaSetting: Setting<Boolean>
   private val rotateSetting: Setting<Boolean>
   val attackSetting: Setting<Boolean>
   internal val renderSetting: Setting<Boolean>
   private val lineWidthSetting: Setting<Float>
   private val fillSetting: Setting<Color>
   private val outlineSetting: Setting<Color>
   internal val fadeSetting: Setting<Boolean>
   private val fadeTimeSetting: Setting<Float>
   private val targetTimeoutTimer: ElapsedTimer
   private val rotationTimeoutTimer: ElapsedTimer
   private val placementDelayTimer: ElapsedTimer
   private val inventoryClickTimer: ElapsedTimer
   private val attackTimer: ElapsedTimer
   private val placementTargets: MutableList<ScaffoldPlacementTarget>
   private var cachedRotations: FloatArray? = null
   private var lastTowerBlockY: Int = 0
   internal var blockSlot: Int
   internal var tellyBaseY: Double = 0.0
   private var savedJumpStrength: Float

   init {
      this.blocksSetting = this.add(RegistrySetSetting<Block>("Blocks", BuiltInRegistries.BLOCK))
      this.selectionSetting = this.add(EnumSetting<ListFilterMode>("Selection", ListFilterMode.ANY))
      this.swapSetting = this.add(EnumSetting<ScaffoldSwapChoice>("Swap", ScaffoldSwapChoice.NONE))
      this.modeSetting = this.add(EnumSetting<ScaffoldMode>("Mode", ScaffoldMode.SINGLE))
      this.sizeSetting = this.add(NumberSetting<Int>("Size", 1, 1, 4) { ignored -> this.modeSetting.getValue() == ScaffoldMode.MULTI })
      this.frequencySetting = this.add(NumberSetting<Int>("Frequency", 1, 1, 9) { ignored -> this.modeSetting.getValue() == ScaffoldMode.MULTI })
      this.delaySetting = this.add(NumberSetting<Int>("Delay", 180, 0, 300).withUnit("ms"))
      this.offsetSetting = this.add(NumberSetting<Float>("Offset", 0.0f, 0.0f, 3.0f) { ignored -> this.modeSetting.getValue() == ScaffoldMode.SINGLE })
      this.towerSetting = this.add(EnumSetting<TowerChoice>("Tower", TowerChoice.NONE))
      this.safeWalkSetting = this.add(BooleanSetting("SafeWalk", true))
      this.airPlaceSetting = this.add(BooleanSetting("AirPlace", false))
      this.tellySetting = this.add(BooleanSetting("Telly", false))
      this.andromedaSetting = this.add(BooleanSetting("Andromeda", false))
      this.rotateSetting = this.add(BooleanSetting("Rotate", true))
      this.attackSetting = this.add(BooleanSetting("Attack", true))
      this.renderSetting = this.add(BooleanSetting("Render", true).markVisibilityDependency())
      this.lineWidthSetting = this.add(NumberSetting<Float>("LineWidth", 1.0f, 0.5f, 5.0f) { ignored -> this.isRenderVisible() })
      this.fillSetting = this.add(ColorSetting("Fill", Color(146643455, true)) { ignored -> this.isRenderVisible() })
      this.outlineSetting = this.add(ColorSetting("Outline", Color(-4351489, true)) { ignored -> this.isRenderVisible() })
      this.fadeSetting = this.add(BooleanSetting("Fade", true) { ignored -> this.isRenderVisible() })
      this.fadeTimeSetting = this.add(NumberSetting<Float>("FadeTime", 1.0f, 0.1f, 1.0f) { ignored -> this.isRenderVisible() && this.fadeSetting.getValue()!! }.withUnit("s"))
      this.targetTimeoutTimer = ElapsedTimer()
      this.rotationTimeoutTimer = ElapsedTimer()
      this.placementDelayTimer = ElapsedTimer()
      this.inventoryClickTimer = ElapsedTimer()
      this.attackTimer = ElapsedTimer()
      this.placementTargets = ArrayList()
      this.blockSlot = -1

      this.savedJumpStrength = -1.0f
      val renderer: FeatureBoxRenderer = ClientContext.featureBoxRenderer
      val renderConfiguration: FeatureBoxRenderer.RenderConfiguration = FeatureBoxRenderer.RenderConfiguration(
         this, this.fillSetting, this.outlineSetting, this.lineWidthSetting, this.fadeTimeSetting,
         ValueProvider<Boolean> { false }, this.fadeSetting, getRenderFadeTimeScale()
      )
      renderer.register(renderConfiguration)
   }

   override fun onEnable() {
      if (!this.isWorldUnavailable()) {
         this.tellyBaseY = ClientAccess.collectiveMinecraft.player!!.getY()
      }
   }

   override fun onToggle() {
      // GRIM-башня успевает ослабить JUMP_STRENGTH: восстанавливаем значение
      // и при выключении, иначе до следующего tickPlacement оно не вернётся.
      this.restoreJumpStrength()
      val targetTimer: ElapsedTimer = this.targetTimeoutTimer
      targetTimer.reset()
      val rotationTimer: ElapsedTimer = this.rotationTimeoutTimer

      rotationTimer.reset()
      this.placementTargets.clear()
      this.cachedRotations = null
      this.lastTowerBlockY = 0
      this.blockSlot = -1
   }

   private fun getJumpStrength(): Float {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val jumpAttribute = Attributes.JUMP_STRENGTH
      return player.getAttribute(jumpAttribute)!!.getBaseValue().toFloat()
   }

   private fun tickPlacement() {
      ClientContext.playerTickController.releaseMultiplier(this)
      this.restoreJumpStrength()

      if (ClientAccess.collectiveMinecraft.player!!.isHandsBusy()) {
         return
      }
      val clickTimer: ElapsedTimer = this.inventoryClickTimer
      if (!clickTimer.hasElapsed(50L)) {
         if (ClientAccess.collectiveMinecraft.player!!.onGround()) {
            if (antiCheatModule!!.movementSyncSetting.getValue()!!) {
               return
            }
         }
      }

      if (!MovementPhysics.isLocalPlayerMoving() || ClientAccess.collectiveMinecraft.player!!.horizontalCollision) {
         this.tellyBaseY = ClientAccess.collectiveMinecraft.player!!.getY()
      }

      this.selectBlockSlot()

      val targets: MutableList<ScaffoldPlacementTarget> = ArrayList()
      if (this.modeSetting.getValue() == ScaffoldMode.MULTI) {
         targets.addAll(ScaffoldPlacementTarget.findMultiFor(this, this.sizeSetting.getValue()!!, this.frequencySetting.getValue()!!))
      } else {
         val target: ScaffoldPlacementTarget? = ScaffoldPlacementTarget.findFor(this)
         if (target != null) {
            targets.add(target)
         }
      }

      if (this.andromedaSetting.getValue()!!) {
         val ceilingTargets = this.findAndromedaCeilingTargets(targets)
         targets.addAll(0, ceilingTargets)
      }

      this.placementTargets.clear()
      this.placementTargets.addAll(targets)
      var missingUsableBlock = true
      if (this.blockSlot != -1) {
         val blockInventory: Inventory = ClientAccess.collectiveMinecraft.player!!.getInventory()
         val currentBlockSlot: Int = this.blockSlot
         missingUsableBlock = blockInventory.getItem(currentBlockSlot).isEmpty()
      }

      if (this.swapSetting.getValue() == ScaffoldSwapChoice.NONE) {
         val heldStack: ItemStack = ClientAccess.collectiveMinecraft.player!!.getMainHandItem()

         if (this.scoreBlockStack(heldStack) == 0) {
            missingUsableBlock = true
         }
      }

      if (missingUsableBlock) {
         this.placementTargets.clear()
      } else {
         for (target in this.placementTargets) {
            if (this.attackBlockingEntity(target)) {
               break
            }
         }

         this.updateTower()
         val delayTimer: ElapsedTimer = this.placementDelayTimer
         val delayMillis: Long = if (this.andromedaSetting.getValue()!!) 0L else this.delaySetting.getValue()!!.toLong()
         if (delayTimer.hasElapsed(delayMillis)) {
            for (target in this.placementTargets) {
               if (this.canPlaceAt(target.position, this.blockSlot)) {
                  val placementSwap: ScaffoldSwapChoice = this.swapSetting.getValue()!!
                  val placementSlot: Int = this.blockSlot
                  placementSwap.runWithSlot(placementSlot, Runnable { this.placeCurrentTarget(target) })
               }
            }
         }
      }

      this.updatePlacementRotation()
      val timeoutTimer: ElapsedTimer = this.targetTimeoutTimer

      if (timeoutTimer.hasElapsed(150L)) {
         this.targetTimeoutTimer.reset()
         this.placementTargets.clear()
      }
   }

   private enum class ScaffoldMode(private val displayName: String) : NamedEntry {
      SINGLE("Single"), MULTI("Multi");
      override fun getName(): String {
         return this.displayName
      }
   }

   private fun placeCurrentTarget(target: ScaffoldPlacementTarget) {
      if (this.renderSetting.getValue()!!) {
         val boxRenderer: FeatureBoxRenderer = ClientContext.featureBoxRenderer

         val renderPosition: BlockPos = target.position
         boxRenderer.addBox(this, renderPosition)
      }

      var placementTargetSnapshot: ScaffoldPlacementTarget = target
      var geometry: ScaffoldPlacementGeometry = ScaffoldPlacementGeometry.fromTarget(
         placementTargetSnapshot, true
      )

      var aimPosition: Vec3 = geometry.aimPosition
      val playerY: Double = ClientAccess.collectiveMinecraft.player!!.getY()
      val fractionPlayerY: Double = ClientAccess.collectiveMinecraft.player!!.getY()
      val supportHeight: Float = (playerY - Math.floor(fractionPlayerY)).toFloat()

      if (geometry.supportDirection == Direction.DOWN) {
         val verticalAxis: Direction.Axis = Axis.Y

         val targetY: Double = target.position.getY().toDouble()
         aimPosition = aimPosition.with(verticalAxis, targetY)
      } else {
         val supportShape: VoxelShape = geometry.supportShape
         if (!supportShape.isEmpty()) {
            val heightShape: VoxelShape = geometry.supportShape
            val playerFraction: Double = supportHeight.toDouble()
            val shapeTop: Double = heightShape.bounds().maxY

            val aimOffsetY: Double = Math.max(shapeTop, playerFraction) - 0.05
            aimPosition = aimPosition.add(0.0, aimOffsetY, 0.0)
         } else {
            val emptyShapeOffsetY: Double = MathConstants.HALF
            aimPosition = aimPosition.add(0.0, emptyShapeOffsetY, 0.0)
         }
      }

      if (placementTargetSnapshot.airPlace) {
         aimPosition = placementTargetSnapshot.position.getCenter()
         geometry = ScaffoldPlacementGeometry(geometry.supportDirection, aimPosition, Shapes.empty())
      }

      val hand: InteractionHand = InteractionHand.MAIN_HAND

      placementTargetSnapshot = target

      val blockPosition: BlockPos = placementTargetSnapshot.position

      val supportDirection: Direction? = geometry.supportDirection
      val airPlaceEnabled: Boolean = this.airPlaceSetting.getValue()!!
      BlockInteractionUtils.placeBlock(
         blockPosition, aimPosition, supportDirection, airPlaceEnabled, hand
      )

      if (RotationManager.isSilentRotationMode()) {
         val postRotations: FloatArray = this.computePostPlacementRotations(geometry, aimPosition)

         this.applyRotation(postRotations)
      }

      this.placementDelayTimer.reset()

      this.rotationTimeoutTimer.reset()

      this.targetTimeoutTimer.reset()
   }

   private fun attackBlockingEntity(target: ScaffoldPlacementTarget?): Boolean {
      if (target == null) {
         return false
      }
      val attackEnabled: Boolean = this.attackSetting.getValue()!!
      if (!attackEnabled) {
         return false
      }
      val timer: ElapsedTimer = this.attackTimer
      val crystalManager: CrystalInteractionManager = ClientContext.crystalInteractionManager
      val attackDelayMillis: Long = crystalManager.getAttackDelayMillis().toLong()
      if (!timer.hasElapsed(attackDelayMillis)) {
         return false
      }

      val targetPosition: BlockPos = target.position
      val entityCheckFlags: Int = ClientContext.crystalInteractionManager.getMinimumCrystalAge()
      val blockingEntity: Entity? = BlockInteractionUtils.attackCrystalAt(targetPosition, entityCheckFlags)
      if (blockingEntity != null) {
         val attackRotations: FloatArray = RotationManager.getRotationsToEntity(blockingEntity)

         this.applyRotation(attackRotations)
         val attackTimerSnapshot: ElapsedTimer = this.attackTimer
         attackTimerSnapshot.reset()
         return true
      }
      return false
   }

   @EventHandler
   private fun onLocalPlayerMoveEvent(event: LocalPlayerMoveEvent) {
      val safeWalkSetting: Setting<Boolean> = this.safeWalkSetting
      if (safeWalkSetting.getValue()!! && !this.andromedaSetting.getValue()!!) {
         applySafeWalk(event)
      }
   }

   @EventHandler
   private fun onKeyboardInputTickEvent(event: KeyboardInputTickEvent) {
      if (!this.andromedaSetting.getValue()!!) return
      val player = ClientAccess.collectiveMinecraft.player ?: return
      if (!MovementPhysics.isLocalPlayerMoving()) return

      InputUtils.setSprint(event.input, true)
      if (player.onGround()) {
         InputUtils.setJump(event.input, true)
      } else {
         InputUtils.setJump(event.input, false)
      }
   }

   @EventHandler
   private fun onConnectionSendPacketEvent(
      event: PacketEvent.Send
   ) {
      if (event.isCancelled()) return

      if (event.packet !is ServerboundContainerClickPacket) return

      val inventoryClickTimer: ElapsedTimer = this.inventoryClickTimer
      inventoryClickTimer.reset()
   }

   private fun computePrePlacementRotations(): FloatArray {
      val target: ScaffoldPlacementTarget = this.placementTargets.first()
      val geometry: ScaffoldPlacementGeometry = ScaffoldPlacementGeometry.fromTarget(target, true)
      var eyePosition: Vec3 = ClientAccess.collectiveMinecraft.player!!.getEyePosition()

      if (RotationManager.isMovementSyncEnabled()) {
         eyePosition = ClientContext.movementPredictionManager.getPredictedEyePosition()
      }

      val aimBasePosition: Vec3 = geometry.aimPosition

      val shape: VoxelShape = geometry.supportShape
      val aimHeight: Double
      if (shape.isEmpty()) {
         aimHeight = MathConstants.HALF
      } else {
         aimHeight = geometry.supportShape.bounds().maxY - 0.01
      }

      val aimPosition: Vec3 = aimBasePosition.add(0.0, aimHeight, 0.0)

      val rotations: FloatArray = RotationManager.calculateRotations(eyePosition, aimPosition)

      if (MovementPhysics.isLocalPlayerMoving()) {
         if (!RotationManager.isMovementSyncEnabled()) {
            val playerYawSnapshot: Float = ClientAccess.collectiveMinecraft.player!!.getYRot()
            val movementInput: ClientInput = ClientAccess.collectiveMinecraft.player!!.input

            val movementDirection: DoubleArray = MovementPhysics.getHorizontalMotion(
               playerYawSnapshot, movementInput, 1.0
            )
            val directionZ: Double = movementDirection[1]
            val movementDirectionX: Double = movementDirection[0]
            val movementRadians: Double = Math.atan2(directionZ, movementDirectionX)

            val movementYaw: Float = (Math.toDegrees(movementRadians) - 270.0).toFloat()
            val placementYaw: Float = rotations[0]

            if (Mth.degreesDifferenceAbs(movementYaw, placementYaw) < 60.0f) {
               rotations[0] = movementYaw
            }
         }
      }

      if (RotationManager.isMovementSyncEnabled()) {
         if (!MovementPhysics.isLocalPlayerMoving()) {
            val stationaryYawBase: Float = rotations[0]
            val random: ThreadLocalRandom = ThreadLocalRandom.current()

            rotations[0] = stationaryYawBase + random.nextInt(8) * AntiCheatModule.ROTATION_JITTER_STEP
         }
      }

      this.rotationTimeoutTimer.reset()
      return rotations
   }

   private fun computePostPlacementRotations(geometry: ScaffoldPlacementGeometry, postAimPosition: Vec3): FloatArray {
      val postShape: VoxelShape = geometry.supportShape
      val postAimHeight: Double
      if (postShape.isEmpty()) {
         postAimHeight = MathConstants.HALF
      } else {
         postAimHeight = geometry.supportShape.bounds().maxY - 0.01
      }

      val shiftedAimPosition: Vec3 = postAimPosition.add(0.0, postAimHeight, 0.0)

      return RotationManager.getRotationsToPosition(shiftedAimPosition)
   }

   internal fun findSupportHeight(sampleY: Float): Double {
      var bounds: AABB = ClientAccess.collectiveMinecraft.player!!.getBoundingBox()
      var onGround: Boolean = ClientAccess.collectiveMinecraft.player!!.onGround()
      if (ClientAccess.collectiveMinecraft.player!!.isPassenger()) {
         bounds = ClientAccess.collectiveMinecraft.player!!.getVehicle()!!.getBoundingBox()
         onGround = ClientAccess.collectiveMinecraft.player!!.getVehicle()!!.onGround()
      }

      val minimumX: Double = bounds.minX
      val minimumZ: Double = bounds.minZ
      val maximumX: Double = bounds.maxX
      val maximumZ: Double = bounds.maxZ
      val samplePoints: MutableList<Vec3> = ArrayList()
      var bottomCenter: Vec3 = BoundingBoxUtils.getBottomCenter(bounds)
      val sampleHeight: Float = sampleY
      val sampleAxis: Direction.Axis = Axis.Y
      samplePoints.add(bottomCenter.with(sampleAxis, sampleHeight.toDouble()))
      if (onGround) {
         bottomCenter = Vec3(minimumX, sampleY.toDouble(), minimumZ)
         val maximumXCorner: Vec3 = Vec3(maximumX, sampleY.toDouble(), minimumZ)
         val maximumZCorner: Vec3 = Vec3(minimumX, sampleY.toDouble(), maximumZ)
         val maximumCorner: Vec3 = Vec3(maximumX, sampleY.toDouble(), maximumZ)
         samplePoints.addAll(listOf(bottomCenter, maximumXCorner, maximumZCorner, maximumCorner))
      }

      var supportHeight: Double = -999.0
      for (sample in samplePoints) {
         val builder: BlockRaycastContext.Builder = BlockRaycastContext.Builder(sample, sample.add(0.0, -1.0, 0.0))
         val raycastEntity: Entity
         if (ClientAccess.collectiveMinecraft.player!!.isPassenger()) {
            raycastEntity = ClientAccess.collectiveMinecraft.player!!.getVehicle()!!
         } else {
            raycastEntity = ClientAccess.collectiveMinecraft.player!!
         }

         val context: BlockRaycastContext = builder.withEntity(raycastEntity).build()
         val hit: BlockHitResult = RaycastUtils.raycast(context)
         val sampleHeightResult: Double
         if (hit.getType() == Type.MISS) {
            val sampleOriginY: Double = sample.y

            sampleHeightResult = sampleOriginY - 1.0
         } else {
            val hitY: Double = hit.getLocation().y

            sampleHeightResult = hitY - 0.009999999776482582
         }

         val previousSupportHeight: Double = supportHeight
         supportHeight = Math.max(previousSupportHeight, sampleHeightResult)
      }

      return supportHeight
   }

   private fun setJumpStrength(strength: Float) {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val jumpAttribute = Attributes.JUMP_STRENGTH
      val attribute: AttributeInstance = player.getAttribute(jumpAttribute)!!
      val baseValue: Double = strength.toDouble()
      attribute.setBaseValue(baseValue)
   }

   private fun restoreJumpStrength() {
      if (this.savedJumpStrength <= 0.0f) return
      val savedStrength: Float = this.savedJumpStrength
      this.savedJumpStrength = -1.0f
      if (this.isWorldUnavailable()) return
      this.setJumpStrength(savedStrength)
   }

   @EventHandler
   private fun onPlayerIsStayingOnGroundSurfaceEvent(event: PlayerIsStayingOnGroundSurfaceEvent) {
      if (this.safeWalkSetting.getValue()!!) {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         if (!player.isHandsBusy()) {
            event.cancel()
         }
      }
   }

   @EventHandler
   private fun onLocalPlayerSendPositionEvent(event: LocalPlayerSendPositionEvent) {
      val rotateEnabled: Boolean = this.rotateSetting.getValue()!!
      if (!rotateEnabled) {
         if (event.stage.isPre()) {
            this.tickPlacement()
         }
      }
   }

   private fun scoreBlockStack(stack: ItemStack): Int {
      if (stack.getItem() !is BlockItem) return 0

      val blockItem: BlockItem = stack.getItem() as BlockItem
      val block: Block = blockItem.getBlock()
      if (!(block as BlockBehaviourAccessor).isCollidable()) {
         return 0
      }

      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      if (!player.isShiftKeyDown() && block == Blocks.POWDER_SNOW) {
         return 0
      }

      if (!this.canUseFallingBlock(blockItem)) {
         return 0
      }

      val filterMode: ListFilterMode = this.selectionSetting.getValue()!!
      val selectedBlocks: Collection<*> = this.blocksSetting.getValue()!!
      if (!ListFilterMode.matches(filterMode, block, selectedBlocks)) return 0

      val blockState: BlockState = block.defaultBlockState()
      val blockLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val shapePosition: BlockPos = BlockPos.ZERO
      val collisionShape: VoxelShape = blockState.getCollisionShape(blockLevel, shapePosition)
      val score: Int
      if (BoundingBoxUtils.hasUnitDimensions(collisionShape)) {
         score = 2
      } else {
         score = 1
      }

      return score

   }

   private fun selectBlockSlot() {
      var stack: ItemStack = ClientAccess.collectiveMinecraft.player!!.getMainHandItem()

      if (this.scoreBlockStack(stack) > 0) {
         this.blockSlot = ClientAccess.collectiveMinecraft.player!!.getInventory().selected
         return
      }

      var bestSlot: Int = ClientAccess.collectiveMinecraft.player!!.getInventory().selected
      stack = ClientAccess.collectiveMinecraft.player!!.getInventory().getItem(bestSlot)

      var bestScore: Int = this.scoreBlockStack(stack)
      var hotbarIndex = 0
      while (true) {
         if (hotbarIndex >= (9)) {
            if (bestScore == 0) {
               this.blockSlot = -1
            } else {
               this.blockSlot = bestSlot
            }

            return
         }

         stack = ClientAccess.collectiveMinecraft.player!!.getInventory().getItem(hotbarIndex)

         val candidateScore: Int = this.scoreBlockStack(stack)
         if (candidateScore > bestScore) {
            bestSlot = hotbarIndex
            bestScore = candidateScore
         }

         hotbarIndex++
      }
   }

   private fun updateTower() {
      val fastTower: Boolean = this.towerSetting.getValue() == TowerChoice.FAST
      val playerY: Double = ClientAccess.collectiveMinecraft.player!!.getY()
      val blockY: Int = Math.floor(playerY).toInt()
      if (this.lastTowerBlockY == blockY && !RotationManager.isMovementSyncEnabled()) {
         return
      }

      val setbackTimer: ElapsedTimer = ClientContext.serverStateManager.getSetbackTimer()

      if (!setbackTimer.hasElapsed(200L) && fastTower) return

      this.lastTowerBlockY = blockY
      if (this.towerSetting.getValue() == TowerChoice.NONE) return

      val input: Input = ClientAccess.collectiveMinecraft.player!!.input.keyPresses
      if (!input.jump() ||
         ClientAccess.collectiveMinecraft.player!!.zza != 0.0f ||
         ClientAccess.collectiveMinecraft.player!!.xxa != 0.0f) return

      if (this.towerSetting.getValue() == TowerChoice.GRIM) {
         if (ClientAccess.collectiveMinecraft.player!!.onGround()) {
            this.savedJumpStrength = this.getJumpStrength()

            val grimJumpStrength: Float = 0.36813f

            this.setJumpStrength(grimJumpStrength)
         }
      } else {
         if (fastTower) {
            val playerTicks: TickRateController.PlayerTicks = ClientContext.playerTickController

            val towerMultiplier: Float = 2.0f
            playerTicks.requestMultiplier(this, towerMultiplier)
         }

         ClientAccess.collectiveMinecraft.player!!.setDeltaMovement(0.0, 0.41999998688698, 0.0)
      }
   }

   private fun updatePlacementRotation() {
      var rotationTimeout = 500
      if (RotationManager.isMovementSyncEnabled()) {
         if (antiCheatModule!!.is2b2tEnabled()) {
            rotationTimeout = 200
         } else {
            rotationTimeout = -1
         }
      }

      if (!RotationManager.isSilentRotationMode() &&
         !this.placementTargets.isEmpty()) {
         val rotations: FloatArray = this.computePrePlacementRotations()

         this.applyRotation(rotations)
      }

      if (this.cachedRotations != null) {
         val rotationManager: RotationManager = ClientContext.rotationManager
         val cachedRotation: FloatArray = this.cachedRotations!!

         rotationManager.requestRotation(cachedRotation, 150)
      }

      val rotationTimeoutMillis: Long = rotationTimeout.toLong()
      if (this.rotationTimeoutTimer.hasElapsed(rotationTimeoutMillis)) {
         this.cachedRotations = null
      }
   }

   @EventHandler
   private fun onRotationEvent(event: RotationRequestEvent) {
      if (this.rotateSetting.getValue()!!) {
         this.tickPlacement()
      }
   }

   internal fun canPlaceAt(position: BlockPos, slot: Int): Boolean {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val inventory: Inventory = player.getInventory()
      val placementSlot: Int
      if (slot == -1) {
         placementSlot = ClientAccess.collectiveMinecraft.player!!.getInventory().selected
      } else {
         placementSlot = slot
      }

      val stack: ItemStack = inventory.getItem(placementSlot)
      if (stack.getItem() is BlockItem) {
         val blockItem: BlockItem = stack.getItem() as BlockItem
         val collisionBlock: Block = blockItem.getBlock()
         if (!(collisionBlock as BlockBehaviourAccessor).isCollidable()) {
            val survivalBlock: Block = blockItem.getBlock()
            val placementState: BlockState = survivalBlock.defaultBlockState()
            val placementLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
            if (!placementState.canSurvive(placementLevel, position)) {
               return false
            }
         }

         val block: Block = blockItem.getBlock()
         val attackEnabled: Boolean = this.attackSetting.getValue()!!
         return BlockInteractionUtils.canPlaceBlock(position, block, true, attackEnabled)
      } else {
         val fallbackAttackEnabled: Boolean = this.attackSetting.getValue()!!
         return BlockInteractionUtils.canPlaceAt(position, true, fallbackAttackEnabled)
      }
   }

   internal fun isSelectedBlockNonCollidable(): Boolean {
      if (this.blockSlot == -1) {
         return false
      }

      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val inventory: Inventory = player.getInventory()
      val slot: Int = this.blockSlot
      val stack: ItemStack = inventory.getItem(slot)
      if (stack.getItem() is BlockItem) {
         val block: Block = (stack.getItem() as BlockItem).getBlock()
         return !(block as BlockBehaviourAccessor).isCollidable()
      }
      return false
   }

   private fun canUseFallingBlock(blockItem: BlockItem): Boolean {
      val groundDistance: Int = RaycastUtils.getGroundDistance()
      return !(blockItem.getBlock() is FallingBlock) || groundDistance <= 1
   }

   private fun applyRotation(rotationsInput: FloatArray) {
      if (this.rotateSetting.getValue()!!) {
         var rotations: FloatArray = rotationsInput
         if (RotationManager.isMovementSyncEnabled()) {
            rotations = RotationManager.addYawJitter(rotations)
         }

         if (RotationManager.isSilentRotationMode()) {
            val renderRotationManager: RotationManager = ClientContext.rotationManager

            renderRotationManager.setRenderRotations(rotations, 10)
            val requestRotationManager: RotationManager = ClientContext.rotationManager

            requestRotationManager.requestRotation(rotations, 150)
         } else {
            this.cachedRotations = rotations
         }
      }
   }

   companion object {
      private var antiCheatModule: AntiCheatModule? = null

      @JvmStatic
      fun bindReferencedModules(moduleRegistry: ModuleRegistry) {
         antiCheatModule = moduleRegistry.getModule(AntiCheatModule::class.java) as AntiCheatModule?
      }

      private fun applySafeWalk(event: LocalPlayerMoveEvent) {
         if (!RotationManager.isMovementSyncEnabled()) return

         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         if (!player.onGround()) return

         if (ClientAccess.collectiveMinecraft.player!!.isHandsBusy()) return

         val playerBounds: AABB = ClientAccess.collectiveMinecraft.player!!.getBoundingBox()
         val eventMovementX: Double = event.x
         val movementZ: Double = event.z
         val movedBounds: AABB = playerBounds.move(eventMovementX, 0.0, movementZ)
         val wasSneaking: Boolean = ClientAccess.collectiveMinecraft.player!!.isShiftKeyDown()
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!

         val expandedBounds: AABB = movedBounds.expandTowards(0.0, -1.0, 0.0)
         if (level.noCollision(expandedBounds)) {
            if (!wasSneaking) {
               ClientAccess.collectiveMinecraft.player!!.setShiftKeyDown(true)
            }

            event.setHorizontalVelocity(0.0, 0.0)
         } else if (!wasSneaking) {
            ClientAccess.collectiveMinecraft.player!!.setShiftKeyDown(false)
         }
      }

      private fun getRenderFadeTimeScale(): Int {
         return 450
      }

   }

   private fun findAndromedaCeilingTargets(floorTargets: List<ScaffoldPlacementTarget>): List<ScaffoldPlacementTarget> {
      val level = ClientAccess.collectiveMinecraft.level ?: return emptyList()
      val results = ArrayList<ScaffoldPlacementTarget>()
      val checkedPositions = HashSet<BlockPos>()

      val playerOrigin = ScaffoldPlacementTarget.findOrigin(this)
      val supportY = this.findSupportHeight(playerOrigin.y.toFloat())
      val currentFloor = BlockPos.containing(playerOrigin.x, supportY, playerOrigin.z)
      val candidateCeilings = ArrayList<BlockPos>()
      candidateCeilings.add(currentFloor.above(3))

      for (floorTarget in floorTargets) {
         candidateCeilings.add(floorTarget.position.above(3))
      }

      for (pos in candidateCeilings) {
         if (!checkedPositions.add(pos)) continue
         if (!level.getBlockState(pos).canBeReplaced()) continue
         if (!this.canPlaceAt(pos, this.blockSlot)) continue

         val strict = BlockInteractionUtils.isStrictDirectionEnabled()
         var side = BlockInteractionUtils.findSupportDirection(pos, strict)
         val airPlace = this.airPlaceSetting.getValue()!!
         if (side == null && !airPlace) {
            side = BlockInteractionUtils.findSupportDirection(pos, false)
         }
         if (side != null || airPlace) {
            results.add(ScaffoldPlacementTarget(pos, side, side == null))
         }
      }
      return results
   }

   private fun isRenderVisible(): Boolean {
      val renderSetting: Setting<Boolean> = this.renderSetting
      return renderSetting.isExpanded()
   }

   enum class TowerChoice(private val displayName: String) : NamedEntry {
      NONE("None"),
      PLAIN("Plain"),
      FAST("Fast"),
      GRIM("GrimV3");

      override fun getName(): String {
         return this.displayName
      }
   }
}
