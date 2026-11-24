package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.collective.client.ClientAccess;
import me.collective.client.events.BlockCollisionShapeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockCollisions.class)
public class BlockCollisionsMixin {
   @Shadow
   @Final
   private CollisionGetter collisionGetter;
   @Shadow
   @Final
   private MutableBlockPos pos;

   @ModifyExpressionValue(
      method = "computeNext",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/phys/shapes/CollisionContext;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
      )
   )
   private VoxelShape postBlockCollisionShapeEvent(
      VoxelShape originalShape, @Local BlockState blockState
   ) {
      if (this.collisionGetter != Minecraft.getInstance().level) {
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
