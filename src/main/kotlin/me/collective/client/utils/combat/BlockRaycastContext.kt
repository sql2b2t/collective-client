package me.collective.client.utils.combat

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ClipContext.Block
import net.minecraft.world.level.ClipContext.Fluid
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class BlockRaycastContext(
   rayStart: Vec3,
   rayEnd: Vec3,
   blockSelectionMode: Block,
   fluidSelectionMode: Fluid,
   collisionEntity: Entity?,
   overrideClassifier: BlockRaycastClassifier
) {
   val start: Vec3 = rayStart
   val end: Vec3 = rayEnd
   val blockMode: Block = blockSelectionMode
   val fluidMode: Fluid = fluidSelectionMode
   val collisionContext: CollisionContext = if (collisionEntity != null) CollisionContext.of(collisionEntity) else CollisionContext.empty()
   val classifier: BlockRaycastClassifier = overrideClassifier

   fun getBlockShape(
      state: BlockState, level: BlockGetter, blockPos: BlockPos
   ): VoxelShape {
      val selectionMode = this.blockMode
      val collision = this.collisionContext

      return selectionMode.get(state, level, blockPos, collision)
   }

   fun getFluidShape(
      state: FluidState, level: BlockGetter, blockPos: BlockPos
   ): VoxelShape {
      val fluidSelectionMode = this.fluidMode

      val selectedShape: VoxelShape
      if (fluidSelectionMode.canPick(state)) {
         selectedShape = state.getShape(level, blockPos)
      } else {
         selectedShape = Shapes.empty()
      }

      return selectedShape
   }

   class Builder(rayStart: Vec3, rayEnd: Vec3) {
      val start: Vec3 = rayStart
      val end: Vec3 = rayEnd
      var blockMode: Block = Block.COLLIDER
      var fluidMode: Fluid = Fluid.NONE
      var entity: Entity? = Minecraft.getInstance().player
      var classifier: BlockRaycastClassifier = BlockRaycastClassifier.NO_OVERRIDE

      fun withBlockMode(blockSelectionMode: Block): Builder {
         this.blockMode = blockSelectionMode
         return this
      }

      fun withFluidMode(fluidSelectionMode: Fluid): Builder {
         this.fluidMode = fluidSelectionMode
         return this
      }

      fun withEntity(collisionEntity: Entity): Builder {
         this.entity = collisionEntity
         return this
      }

      fun withClassifier(overrideClassifier: BlockRaycastClassifier): Builder {
         this.classifier = overrideClassifier
         return this
      }

      fun withClassifiers(
         vararg overrideClassifiers: BlockRaycastClassifier?
      ): Builder {
         this.classifier = BlockRaycastClassifier.firstNonNone(*overrideClassifiers)
         return this
      }

      fun build(): BlockRaycastContext {
         return BlockRaycastContext(
            this.start,
            this.end,
            this.blockMode,
            this.fluidMode,
            this.entity,
            this.classifier
         )
      }
   }
}
