package me.collective.client.utils.world

import me.collective.client.ClientAccess

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CarpetBlock
import net.minecraft.world.level.block.SnowLayerBlock
import net.minecraft.world.level.block.state.BlockState

class BlockStateUtils private constructor() : ClientAccess {

   companion object {
      private val ignoredCollisionBlocks: Set<Block> = setOf(
         Blocks.FIRE,
         Blocks.SOUL_FIRE,
         Blocks.GLOW_LICHEN,
         Blocks.LIGHT,
         Blocks.TRIPWIRE_HOOK,
         Blocks.TRIPWIRE
      )

      @JvmStatic
      fun isBlastResistantUnbreakable(state: BlockState): Boolean {
         val resistanceBlock = state.block
         val resistance = resistanceBlock.explosionResistance
         return resistance >= 600.0F && state.block.defaultDestroyTime() < 0.0F
      }

      @JvmStatic
      fun isBlocking(state: BlockState): Boolean {
         val block = state.block
         if (state.isAir || ignoredCollisionBlocks.contains(block)) {
            return false
         }
         if (state.`is`(Blocks.SNOW) && hasLowSnowLayers(state)) {
            return false
         }
         val collisionLevel = ClientAccess.collectiveMinecraft.level ?: return true
         val collisionPosition = BlockPos.ZERO
         val shape = state.getCollisionShape(collisionLevel, collisionPosition)
         if (shape.isEmpty) {
            return state.`is`(Blocks.COBWEB)
         }
         return true
      }

      private fun hasLowSnowLayers(state: BlockState): Boolean {
         return state.getValueOrElse(SnowLayerBlock.LAYERS, 0) < 5
      }

      @JvmStatic
      fun isBlocking(position: BlockPos): Boolean {
         val level = ClientAccess.collectiveMinecraft.level ?: return false
         return isBlocking(level.getBlockState(position))
      }

      @JvmStatic
      fun isBlockingExceptCarpet(position: BlockPos): Boolean {
         val level = ClientAccess.collectiveMinecraft.level ?: return false
         val state = level.getBlockState(position)
         return isBlocking(state) && state.block !is CarpetBlock
      }

      @JvmStatic
      fun isBlastResistantBreakable(state: BlockState): Boolean {
         val resistanceBlock = state.block
         val resistance = resistanceBlock.explosionResistance
         return resistance >= 600.0F && state.block.defaultDestroyTime() >= 0.0F
      }

      @JvmStatic
      fun isWeakOrInstantlyBreakable(state: BlockState): Boolean {
         val resistanceBlock = state.block
         val resistance = resistanceBlock.explosionResistance
         if (resistance < 600.0F && state.block.explosionResistance >= 0.0F) {
            return true
         }
         return state.block.defaultDestroyTime() == 0.0F
      }
   }
}
