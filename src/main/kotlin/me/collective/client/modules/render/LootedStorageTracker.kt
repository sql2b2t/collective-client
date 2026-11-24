package me.collective.client.modules.render

import me.collective.client.utils.math.MathConstants

import me.collective.client.ClientAccess
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import java.awt.Color
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.entity.BarrelBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.phys.BlockHitResult

class LootedStorageTracker : ClientAccess {
   private val lootedPositions: MutableSet<BlockPos>

   fun onBlockUpdate(position: BlockPos, state: BlockState) {
      if (state.isAir) {
         this.lootedPositions.remove(position)
      } else {
         val typeProperty: EnumProperty<ChestType> = ChestBlock.TYPE
         val selectedChestType: ChestType = if (state.hasProperty(typeProperty)) {
            val chestTypeProperty: EnumProperty<ChestType> = ChestBlock.TYPE
            state.getValue(chestTypeProperty)
         } else {
            ChestType.SINGLE
         }
         val chestType: ChestType = selectedChestType
         if (chestType != ChestType.SINGLE) {
            val positionsToCheck: MutableSet<BlockPos> = this.lootedPositions
            val partnerDirection: Direction = this.getPartnerDirection(chestType, state)
            if (positionsToCheck.contains(position.relative(partnerDirection))) {
               this.lootedPositions.add(position)
            }
         }
      }
   }

   init {
      this.lootedPositions = HashSet(100)
   }

   fun isLooted(blockEntity: BlockEntity): Boolean {
      if (this.isTrackedStorage(blockEntity)) {
         val positions: MutableSet<BlockPos> = this.lootedPositions
         if (positions.contains(blockEntity.getBlockPos())) {
            return true
         }
      }
      return false
   }

   private fun getPartnerDirection(chestType: ChestType, state: BlockState): Direction {
      val facingProperty: EnumProperty<Direction> = ChestBlock.FACING
      val facing: Direction = state.getValue(facingProperty)
      val facingYaw: Float = facing.toYRot()
      val yawOffset: Int = MathConstants.NINETY_DEGREES
      val partnerYaw: Float = facingYaw + yawOffset * (if (chestType == ChestType.LEFT) 1 else -1)
      val wrappedYaw: Double = Mth.wrapDegrees(partnerYaw).toDouble()
      return Direction.fromYRot(wrappedYaw)
   }

   private fun isTrackedStorage(blockEntity: BlockEntity?): Boolean {
      return blockEntity is ChestBlockEntity || blockEntity is BarrelBlockEntity
   }

   fun onUseItemOn(event: MultiPlayerGameModeUseItemOnEvent) {
      if (!event.isCancelled) {
         val sneakCheckPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         if (!sneakCheckPlayer.isShiftKeyDown) {
            val hit: BlockHitResult = event.hitResult
            val position: BlockPos = hit.getBlockPos()
            val entityLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
            val blockEntity: BlockEntity? = entityLevel.getBlockEntity(position)
            if (this.isTrackedStorage(blockEntity)) {
               if (this.lootedPositions.contains(position)) {
                  return
               }
               val stateLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
               val state: BlockState = stateLevel.getBlockState(position)
               val typeProperty: EnumProperty<ChestType> = ChestBlock.TYPE
               val selectedChestType: ChestType = if (state.hasProperty(typeProperty)) {
                  val chestTypeProperty: EnumProperty<ChestType> = ChestBlock.TYPE
                  state.getValue(chestTypeProperty)
               } else {
                  ChestType.SINGLE
               }
               val chestType: ChestType = selectedChestType
               if (chestType != ChestType.SINGLE) {
                  val positionsToMark: MutableSet<BlockPos> = this.lootedPositions
                  val partnerDirection: Direction = this.getPartnerDirection(chestType, state)
                  positionsToMark.add(position.relative(partnerDirection))
               }
               this.lootedPositions.add(position)
            }
            return
         }
      }
   }

   companion object {
      private val LOOTED_COLOR: Color = Color(217, 199, 255)

      @JvmStatic
      fun getLootedColor(): Color {
         return LOOTED_COLOR
      }
   }
}
