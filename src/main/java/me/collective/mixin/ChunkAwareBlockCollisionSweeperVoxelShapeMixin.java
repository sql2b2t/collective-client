package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.collective.client.ClientAccess;
import me.collective.client.events.BlockCollisionShapeEvent;
import net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net/caffeinemc/mods/lithium/common/entity/movement/ChunkAwareBlockCollisionSweeperVoxelShape", remap = false)
public abstract class ChunkAwareBlockCollisionSweeperVoxelShapeMixin extends ChunkAwareBlockCollisionSweeper {

   public ChunkAwareBlockCollisionSweeperVoxelShapeMixin(Level level, @Nullable Entity entity, AABB bounds, boolean collisionFlag) {
      super(level, entity, bounds, collisionFlag);
   }

   @ModifyExpressionValue(
      method = "computeNext()Lnet/minecraft/world/phys/shapes/VoxelShape;",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/phys/shapes/CollisionContext;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
      )
   )
   private VoxelShape modifyClientBlockCollisionShape(
      VoxelShape originalShape, @Local(name = "state") BlockState blockState
   ) {
      if (this.world != Minecraft.getInstance().level) {
         return originalShape;
      }

      BlockCollisionShapeEvent event = (BlockCollisionShapeEvent)ClientAccess.eventBus
         .post(
            BlockCollisionShapeEvent.prepare(originalShape, this.pos, blockState)
         );
      return event.isCancelled()
         ? Shapes.empty()
         : event.getShape();
   }
}
