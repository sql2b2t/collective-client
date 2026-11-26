package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils

import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.ShulkerBoxBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity.AnimationStatus
import net.minecraft.world.phys.Vec3

class ShulkerCleanupController(module: ChestStealerModule) : ChestStealerController(module) {
   private var wasShulkerScreenOpen: Boolean = false
   private var breakingPosition: BlockPos? = null
   private var breakingDirection: Direction = Direction.UP
   private var breakingTicks: Int = 0

   private fun findOpenShulkerToBreak(): BlockPos? {
      var nearestPosition: BlockPos? = null

      var nearestDistance: Double = 999.0

      for (loadedEntity in WorldUtils.getLoadedBlockEntities()) {
         val blockEntity: BlockEntity = loadedEntity
         if (blockEntity is ShulkerBoxBlockEntity) {
            val shulker: ShulkerBoxBlockEntity = blockEntity
            if (shulker.animationStatus != AnimationStatus.CLOSED) {
               val eyePosition: Vec3 = ClientAccess.collectiveMinecraft.player!!.eyePosition
               val shulkerCenter: Vec3 = Vec3.atCenterOf(blockEntity.blockPos)
               val distance: Double = eyePosition.distanceTo(shulkerCenter)
               if (distance < nearestDistance) {
                  if (distance <= 5.0) {
                     nearestDistance = distance
                     nearestPosition = blockEntity.blockPos
                  }
               }
            }
         }
      }

      return nearestPosition
   }

   fun tick() {
      val shulkerScreenOpen: Boolean = ClientAccess.collectiveMinecraft.gui.screen() is ShulkerBoxScreen
      if (this.wasShulkerScreenOpen && !shulkerScreenOpen) {
         val position: BlockPos? = this.findOpenShulkerToBreak()
         if (position != null) {
            val fallbackDirection: Direction = Direction.UP
            this.breakingDirection = BlockInteractionUtils.getStrictDirectionsOrFallback(position, fallbackDirection).first()
            this.breakingPosition = position
            this.breakingTicks = 0
            val gameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
            gameMode.startDestroyBlock(position, this.breakingDirection)
         }
      }

      this.wasShulkerScreenOpen = shulkerScreenOpen
      this.continueBreaking()
   }

   /**
    * В выживании одного startDestroyBlock недостаточно: шалкер ломается
    * только при continueDestroyBlock на каждом тике до исчезновения блока.
    */
   private fun continueBreaking() {
      val position: BlockPos = this.breakingPosition ?: return
      val level = ClientAccess.collectiveMinecraft.level
      val gameMode: MultiPlayerGameMode? = ClientAccess.collectiveMinecraft.gameMode
      if (level == null || gameMode == null) {
         this.breakingPosition = null
         return
      }
      if (level.getBlockState(position).block !is ShulkerBoxBlock) {
         // блока больше нет — шалкер сломан
         this.breakingPosition = null
         return
      }
      if (this.breakingTicks++ == 0) {
         // в этом тике уже был startDestroyBlock
         return
      }
      if (this.breakingTicks > BREAK_TIMEOUT_TICKS) {
         // сервер не даёт сломать — прекращаем, чтобы не копать вечно
         this.breakingPosition = null
         return
      }
      gameMode.continueDestroyBlock(position, this.breakingDirection)
   }

   companion object {
      private const val BREAK_TIMEOUT_TICKS = 200
   }

}
