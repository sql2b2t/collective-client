package me.collective.client.utils.combat

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

interface BlockRaycastClassifier {
   fun test(blockPos: BlockPos, state: BlockState): BlockRaycastOverride

   companion object {
      @JvmField
      val NO_OVERRIDE: BlockRaycastClassifier = object : BlockRaycastClassifier {
         override fun test(ignoredPos: BlockPos, state: BlockState): BlockRaycastOverride =
            BlockRaycastOverride.NONE
      }

      @JvmField
      val IGNORE_LOW_RESISTANCE: BlockRaycastClassifier = object : BlockRaycastClassifier {
         override fun test(ignoredPos: BlockPos, state: BlockState): BlockRaycastOverride {
            val block = state.block
            return if (block.explosionResistance < 600.0f) BlockRaycastOverride.IGNORE else BlockRaycastOverride.NONE
         }
      }

      @JvmStatic
      fun ignorePosition(ignoredPos: BlockPos): BlockRaycastClassifier =
         object : BlockRaycastClassifier {
            override fun test(blockPos: BlockPos, ignoredState: BlockState): BlockRaycastOverride =
               if (blockPos == ignoredPos) BlockRaycastOverride.IGNORE else BlockRaycastOverride.NONE
         }

      @JvmStatic
      fun ignorePositionSet(ignoredPositions: Set<*>): BlockRaycastClassifier =
         object : BlockRaycastClassifier {
            override fun test(blockPos: BlockPos, ignoredState: BlockState): BlockRaycastOverride =
               if (ignoredPositions.contains(blockPos)) BlockRaycastOverride.IGNORE else BlockRaycastOverride.NONE
         }

      @JvmStatic
      fun ignorePositions(ignoredPositions: Collection<*>): BlockRaycastClassifier {
         if (ignoredPositions.isEmpty()) {
            return NO_OVERRIDE
         }
         if (ignoredPositions is Set<*>) {
            return ignorePositionSet(ignoredPositions)
         }
         return ignorePositions(ignoredPositions as Iterable<*>)
      }

      @JvmStatic
      fun ignorePositions(ignoredPositions: Iterable<*>): BlockRaycastClassifier =
         object : BlockRaycastClassifier {
            override fun test(blockPos: BlockPos, ignoredState: BlockState): BlockRaycastOverride =
               if (containsPosition(ignoredPositions, blockPos)) BlockRaycastOverride.IGNORE else BlockRaycastOverride.NONE
         }

      @JvmStatic
      fun solidPosition(solidPos: BlockPos?): BlockRaycastClassifier {
         if (solidPos == null) {
            return NO_OVERRIDE
         }
         return object : BlockRaycastClassifier {
            override fun test(blockPos: BlockPos, ignoredState: BlockState): BlockRaycastOverride =
               if (blockPos == solidPos) BlockRaycastOverride.SOLID else BlockRaycastOverride.NONE
         }
      }

      @JvmStatic
      fun solidPositions(solidPositions: Iterable<*>): BlockRaycastClassifier =
         object : BlockRaycastClassifier {
            override fun test(blockPos: BlockPos, ignoredState: BlockState): BlockRaycastOverride =
               if (containsPosition(solidPositions, blockPos)) BlockRaycastOverride.SOLID else BlockRaycastOverride.NONE
         }

      @JvmStatic
      fun firstNonNone(vararg classifiers: BlockRaycastClassifier?): BlockRaycastClassifier {
         val activeClassifiers = classifiers
            .filter { classifier -> classifier !== NO_OVERRIDE }
            .toTypedArray()
         if (activeClassifiers.isEmpty()) {
            return NO_OVERRIDE
         }
         if (activeClassifiers.size == 1) {
            return activeClassifiers[0]!!
         }
         return object : BlockRaycastClassifier {
            override fun test(blockPos: BlockPos, state: BlockState): BlockRaycastOverride {
               for (classifier in activeClassifiers) {
                  val override = classifier!!.test(blockPos, state)
                  if (override != BlockRaycastOverride.NONE) {
                     return override
                  }
               }
               return BlockRaycastOverride.NONE
            }
         }
      }

      private fun containsPosition(positions: Iterable<*>, blockPos: BlockPos): Boolean {
         for (value in positions) {
            if (blockPos == (value as BlockPos?)) {
               return true
            }
         }
         return false
      }
   }
}
