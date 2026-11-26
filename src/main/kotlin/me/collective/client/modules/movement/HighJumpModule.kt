package me.collective.client.modules.movement


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LivingEntityJumpFromGroundEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.math.Vec3Utils
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import java.util.function.Predicate
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.FenceBlock
import net.minecraft.world.level.block.ShulkerBoxBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity.AnimationStatus
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

class HighJumpModule : Module("HighJump", "Makes you jump higher.", ModuleCategory.MOVEMENT) {
   companion object {
      private var antiCheatModule: AntiCheatModule? = null

      @JvmStatic
      fun bindReferencedModules(registry: ModuleRegistry) {
         antiCheatModule = registry.getModule(AntiCheatModule::class.java) as AntiCheatModule
      }
   }

   private val modeSetting: Setting<ModeChoice>
   private val strengthSetting: Setting<Float>
   private val inMovementSetting: Setting<Boolean>
   private val infiniteSetting: Setting<Boolean>
   private var remainingBoostTicks: Int = 0
   private var shulkerJumpPending: Boolean = false
   private var shulkerBoostPaused: Boolean = false

   override fun getInfo(): String? {
      return (this.modeSetting.value).getName()
   }

   private fun startShulkerJump(event: LivingEntityJumpFromGroundEvent) {
      val shulkerPos: BlockPos? = this.findShulker(true)
      if (shulkerPos == null) {
         return
      }
      if ((ClientAccess.collectiveMinecraft.level!!.getBlockEntity(shulkerPos) as ShulkerBoxBlockEntity).animationStatus == AnimationStatus.CLOSED) {
         this.interactWithShulker(shulkerPos)
      } else {
         event.jumpPower = 1.0f
      }
      this.shulkerBoostPaused = false
      this.shulkerJumpPending = true
      val configuredStrength: Float = this.strengthSetting.value
      this.remainingBoostTicks = configuredStrength.toInt()
   }

   private fun isShulkerInRange(blockEntity: BlockEntity): Boolean {
      val playerPositionSnapshot: Vec3 = ClientAccess.collectiveMinecraft.player!!.position()
      val blockCenterSnapshot: Vec3 = Vec3.atCenterOf(blockEntity.blockPos)
      val distance: Float = Vec3Utils.horizontalDistance(playerPositionSnapshot, blockCenterSnapshot)
      return distance <= 1.0f
   }

   @EventHandler
   private fun onJumpFromGround(event: LivingEntityJumpFromGroundEvent) {
      val choice: ModeChoice = this.modeSetting.value
      when (choice) {
         ModeChoice.PLAIN -> {
            this.applyPlainJump(event)
         }
         ModeChoice.FENCE -> {
            this.applyFenceJump(event)
         }
         ModeChoice.SHULKER -> {
            this.startShulkerJump(event)
         }
      }
   }

   @EventHandler
   private fun onPlayerTick(event: LocalPlayerPreTickEvent) {
      val openShulkerNearby: Boolean = this.findShulker(false) != null
      val anyShulkerNearby: Boolean = this.findShulker(true) != null
      if (!openShulkerNearby && !this.shulkerJumpPending || !anyShulkerNearby) {
         this.remainingBoostTicks = 0
      }
      if (this.remainingBoostTicks <= 0 || !openShulkerNearby) {
         return
      }
      if (!this.shulkerBoostPaused) {
         var upwardImpulse: Float = 0.908f
         if (antiCheatModule!!.is2b2tEnabled()) {
            upwardImpulse = 1.0f
         } else if (this.remainingBoostTicks == 1) {
            upwardImpulse = 1.051f
         }
         val upwardImpulseDouble: Double = upwardImpulse.toDouble()
         ClientAccess.collectiveMinecraft.player!!.push(0.0, upwardImpulseDouble, 0.0)
         val risingVelocityY: Double = ClientAccess.collectiveMinecraft.player!!.deltaMovement.y
         if (risingVelocityY >= 7.0) {
            this.shulkerBoostPaused = true
         }
      } else {
         val fallingVelocityY: Double = ClientAccess.collectiveMinecraft.player!!.deltaMovement.y
         this.shulkerBoostPaused = fallingVelocityY > -0.2
      }
      this.shulkerJumpPending = false
      if (!this.infiniteSetting.value) {
         this.remainingBoostTicks--
      }
   }

   private fun applyPlainJump(event: LivingEntityJumpFromGroundEvent) {
      if (!this.inMovementSetting.value && MovementPhysics.isLocalPlayerMoving()) {
         return
      }
      val jumpStrength: Float = this.strengthSetting.value
      event.jumpPower = jumpStrength
   }

   private fun findShulker(includeClosed: Boolean): BlockPos? {
      for (blockEntityValue in WorldUtils.getLoadedBlockEntities()) {
         val blockEntity: BlockEntity = blockEntityValue
         if (blockEntity !is ShulkerBoxBlockEntity) {
            continue
         }
         val shulker: ShulkerBoxBlockEntity = blockEntity
         if (!this.isShulkerInRange(shulker)) {
            continue
         }
         if (shulker.animationStatus == AnimationStatus.CLOSED && !includeClosed) {
            continue
         }
         if (shulker.blockState.getValue(ShulkerBoxBlock.FACING) == Direction.UP) {
            return blockEntity.blockPos
         }
      }
      return null
   }

   private fun interactWithShulker(position: BlockPos) {
      val directions = BlockInteractionUtils.getStrictDirections(position)
      val direction: Direction = if (directions.isEmpty()) Direction.UP else directions.first() as Direction
      val hitPoint: Vec3 = Vec3.atCenterOf(position).relative(direction, 0.5)
      val interactionCount: Int = if (this.infiniteSetting.value) 3 else 1
      for (i in 0 until interactionCount) {
         val hit: BlockHitResult = BlockHitResult(hitPoint, direction, position, false)
         PacketSender.sendUseItemOn(InteractionHand.MAIN_HAND, hit)
         ClientContext.swingManager.swingPacket(InteractionHand.MAIN_HAND, SwingAction.INTERACT)
      }
   }

   private fun applyFenceJump(event: LivingEntityJumpFromGroundEvent) {
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val fenceBounds: AABB = ClientAccess.collectiveMinecraft.player!!.boundingBox
      val intersectsFence: Boolean = (level as BlockGetter).getBlockStates(fenceBounds).anyMatch { state -> state.block is FenceBlock }
      if (intersectsFence) {
         event.jumpPower = 0.6f
      }
   }

   init {
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", ModeChoice.PLAIN))
      val showStrength: Predicate<Any> = Predicate { ignored -> this.modeSetting.value != ModeChoice.FENCE }
      this.strengthSetting = this.add(NumberSetting<Float>("Strength", 1.0f, 0.0f, 5.0f, showStrength))
      val showInMovement: Predicate<Any> = Predicate { ignored -> this.modeSetting.value == ModeChoice.PLAIN }
      this.inMovementSetting = this.add(BooleanSetting("InMovement", true, showInMovement))
      val showInfinite: Predicate<Any> = Predicate { ignored -> this.modeSetting.value == ModeChoice.SHULKER }
      this.infiniteSetting = this.add(BooleanSetting("Infinite", false, showInfinite))
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      PLAIN("Plain"),
      FENCE("Fence"),
      SHULKER("Shulker");

      override fun getName(): String {
         return this.displayName
      }
   }
}
