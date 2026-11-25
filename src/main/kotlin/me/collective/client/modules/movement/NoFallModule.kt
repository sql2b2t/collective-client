package me.collective.client.modules.movement


import me.collective.client.ClientAccess
import me.collective.client.utils.math.MathConstants
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.combat.BlockRaycastContext
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.utils.combat.RaycastUtils
import me.collective.client.events.EventStage
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.mixin.ServerboundMovePlayerPacketAccessor
import java.util.HashSet
import java.util.function.Predicate
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.tags.FluidTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult.Type
import net.minecraft.world.phys.Vec3

class NoFallModule : Module("NoFall", "Prevents falling damage.", ModuleCategory.MOVEMENT) {
   val modeSetting: Setting<ModeChoice>
   private val groundDistanceSetting: Setting<Float>
   private var previousFallDistance: Double = 0.0
   private var predictedTopFaceCollision: Boolean = false

   override fun getInfo(): String? {
      return this.modeSetting.value.getName()
   }

   @EventHandler
   private fun onConnectionSendPacketEvent(
      event: PacketEvent.Send
   ) {
      val mode: ModeChoice = this.modeSetting.value
      mode.handleOutgoingPacket(event)
   }

   @EventHandler
   private fun onLocalPlayerPreTickEvent(event: LocalPlayerPreTickEvent) {
      val mode: ModeChoice = this.modeSetting.value
      mode.beforePlayerTick()
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      val mode: ModeChoice = this.modeSetting.value
      mode.afterPlayerTick()
   }

   private fun collectBottomCorners(box: AABB): HashSet<BlockPos> {
      return HashSet(
         listOf(
            BlockPos.containing(box.maxX, box.minY, box.maxZ),
            BlockPos.containing(box.maxX, box.minY, box.minZ),
            BlockPos.containing(box.minX, box.minY, box.maxZ),
            BlockPos.containing(box.minX, box.minY, box.minZ)
         )
      )
   }

   @EventHandler
   private fun onLocalPlayerSendPositionEvent(event: LocalPlayerSendPositionEvent) {
      if (event.stage == EventStage.Pre) {
         this.updatePredictedTopFaceCollision()
      }

      val mode: ModeChoice = this.modeSetting.value
      mode.handleSendPosition(event)
      this.previousFallDistance = ClientContext.playerStateTracker.getFallDistance().toDouble()
   }

   private fun updatePredictedTopFaceCollision() {
      val currentPosition: Vec3 = ClientAccess.collectiveMinecraft.player!!.position()
      val previousX: Double = ClientAccess.collectiveMinecraft.player!!.xo
      val previousY: Double = ClientAccess.collectiveMinecraft.player!!.yo
      val previousZ: Double = ClientAccess.collectiveMinecraft.player!!.zo
      val prediction: Vec3 = currentPosition.subtract(previousX, previousY, previousZ).scale(1.100000023841858)
      val builder = BlockRaycastContext.Builder(
         ClientAccess.collectiveMinecraft.player!!.position(), ClientAccess.collectiveMinecraft.player!!.position().add(prediction)
      )
      val context: BlockRaycastContext = builder.build()
      val hit: BlockHitResult = RaycastUtils.raycast(context)
      if (hit.type == Type.MISS) {
         this.predictedTopFaceCollision = false
      } else {
         this.predictedTopFaceCollision = hit.direction == Direction.UP
      }
   }

   init {
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", ModeChoice.PACKET))
      val groundDistanceVisible = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.MLG }
      this.groundDistanceSetting = this.add(
         NumberSetting<Float>("GroundDistance", 2.0f, 1.0f, 4.0f, groundDistanceVisible).withUnit("m")
      )

      for (choice in ModeChoice.entries) {
         choice.module = this
      }
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      PACKET("Packet") {
         override fun handleOutgoingPacket(
            event: PacketEvent.Send
         ) {
            if (event.packet is ServerboundMovePlayerPacket &&
         PlayerStateUtils.getTrackedFallDistance().toDouble() > 2.1 &&
         !PlayerStateUtils.isFallFlying()) {
               (event.packet as ServerboundMovePlayerPacketAccessor).setOnGround(true)
            }
         }

         override fun handleSendPosition(event: LocalPlayerSendPositionEvent) {
            if (ClientAccess.collectiveMinecraft.player!!.isFallFlying()) {
               GRIM.handleSendPosition(event)
            }
         }
      },
      ANTI("Anti") {
         override fun handleOutgoingPacket(
            event: PacketEvent.Send
         ) {
            if (event.packet !is ServerboundMovePlayerPacket ||
         !(PlayerStateUtils.getTrackedFallDistance() > 3.0f) ||
         !this.canApplyFallProtection()) {
               return
            }
            val world: ClientLevel = ClientAccess.collectiveMinecraft.level!!
            val bounds: AABB = ClientAccess.collectiveMinecraft.player!!.getBoundingBox()
            val verticalExpansion: Double = ClientAccess.collectiveMinecraft.player!!.getDeltaMovement().y * 1.1
            val expandedBounds: AABB = bounds.expandTowards(0.0, verticalExpansion, 0.0)
            if (world.noCollision(expandedBounds)) {
               return
            }
            val target: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val x: Double = ClientAccess.collectiveMinecraft.player!!.getX()
            val y: Double = ClientAccess.collectiveMinecraft.player!!.getY() + 2.0
            val z: Double = ClientAccess.collectiveMinecraft.player!!.getZ()
            target.setPosRaw(x, y, z)
         }
      },
      TELEPORT("Teleport") {
         private var teleportPending: Boolean = false

         override fun afterPlayerTick() {
            if (PlayerStateUtils.getTrackedFallDistance() > 3.0f) {
               this.teleportPending = true
            }
         }

         override fun handleSendPosition(event: LocalPlayerSendPositionEvent) {
            if (!this.teleportPending) {
               return
            }
            if (!event.onGround) {
               return
            }
            if (event.stage != EventStage.Pre) {
               return
            }
            if (!this.canApplyFallProtection()) {
               return
            }
            event.x = 0.0
            event.y = 0.0
            event.z = 0.0
            event.onGround = false
            if (WorldUtils.getDimension().isEnd()) {
               event.y = 64.0
            }
            this.teleportPending = false
         }
      },
      MLG("MLG") {
         private var skipNextMlgTick: Boolean = false

         override fun afterPlayerTick() {
            if (this.skipNextMlgTick) {
               this.skipNextMlgTick = false
               return
            }
            val bucket: InventoryUtils.SwapSlot = InventoryUtils.findSwapSlot(Items.WATER_BUCKET, true)
            val selectedSlot: Int = ClientAccess.collectiveMinecraft.player!!.getInventory().selected
            if (PlayerStateUtils.getTrackedFallDistance() <= 3.0f) {
               return
            }
            if (!bucket.isFound()) {
               return
            }
            if (ClientAccess.collectiveMinecraft.player!!.isFallFlying()) {
               return
            }
            val owner: NoFallModule = this.module!!
            val bounds: AABB = ClientAccess.collectiveMinecraft.player!!.getBoundingBox()
            val positions: HashSet<BlockPos> = owner.collectBottomCorners(bounds)
            val playerPosition: Vec3 = ClientAccess.collectiveMinecraft.player!!.position()
            positions.add(BlockPos.containing(playerPosition))
            var depth: Int = 0
            while (depth.toFloat() <= this.module!!.groundDistanceSetting.value) {
               for (position in positions) {
                  val blockWorld: ClientLevel = ClientAccess.collectiveMinecraft.level!!
                  val below: BlockPos = position.relative(Direction.DOWN, depth)
                  val state: BlockState = blockWorld.getBlockState(below)
                  if (state.block !is AirBlock) {
                     val fluidWorld: ClientLevel = ClientAccess.collectiveMinecraft.level!!

                     if (fluidWorld.getFluidState(position).`is`(FluidTags.WATER)) {
                        return
                     }
                     val pitchPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
                     val pitch: Float = MathConstants.NINETY_DEGREES.toFloat()
                     pitchPlayer.setXRot(pitch)
                     InventoryUtils.swapTo(bucket)
                     val gameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
                     val usePlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
                     val hand: InteractionHand = bucket.hand
                     gameMode.useItem(usePlayer, hand)
                     val swingPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
                     val swingHand: InteractionHand = bucket.hand
                     swingPlayer.swing(swingHand)
                     InventoryUtils.swapBack(selectedSlot, bucket)
                     this.skipNextMlgTick = true
                     return
                  }
               }
               depth++
            }
         }
      },
      GRIM("Grim") {
         override fun handleSendPosition(event: LocalPlayerSendPositionEvent) {
            if (event.stage == EventStage.Post) {
               return
            }
            if (!ClientAccess.collectiveMinecraft.player!!.onGround() || this.module!!.previousFallDistance < 3.0) {
               return
            }
            val position: Vec3 = ClientContext.playerStateTracker.getPacketPosition()
            val x: Double = position.x
            val y: Double = position.y + 1.0E-9
            val z: Double = position.z
            val yaw: Float = ClientAccess.collectiveMinecraft.player!!.getYRot()
            val pitch: Float = ClientAccess.collectiveMinecraft.player!!.getXRot()
            PacketSender.sendPositionRotation(
               x, y, z, yaw, pitch, false
            )
         }
      },
      GRIM_JUMP("GrimJump") {
         private var jumpPending: Boolean = false

         override fun handleOutgoingPacket(
            event: PacketEvent.Send
         ) {
            if ((event.packet is ServerboundMovePlayerPacket || event.packet is ServerboundMovePlayerPacketAccessor) &&
         (PlayerStateUtils.getTrackedFallDistance() > 3.0f || this.jumpPending)) {
               if (!ClientAccess.collectiveMinecraft.player!!.isFallFlying()) {
                  (event.packet as ServerboundMovePlayerPacketAccessor).setOnGround(false)
                  this.jumpPending = true
               }
            }
         }

         override fun beforePlayerTick() {
            if (!this.jumpPending) {
               return
            }
            if (!ClientAccess.collectiveMinecraft.player!!.onGround()) {
               return
            }
            ClientContext.inputOverrides.requestJump(true)
            this.jumpPending = false
         }
      };

      internal var module: NoFallModule? = null

      override fun getName(): String {
         return this.displayName
      }

      open fun afterPlayerTick() {
      }

      open fun handleOutgoingPacket(
         event: PacketEvent.Send
      ) {
      }

      open fun handleSendPosition(event: LocalPlayerSendPositionEvent) {
      }

      open fun beforePlayerTick() {
      }

      protected fun canApplyFallProtection(): Boolean {
         if (!ClientAccess.collectiveMinecraft.player!!.isFallFlying()) {
            return true
         }

         val pitch: Float = ClientContext.playerStateTracker.getPacketRotations()[1]
         return Math.abs(pitch) <= 45.0f || this.module!!.predictedTopFaceCollision
      }
   }
}
