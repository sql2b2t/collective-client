package me.collective.mixin;

import me.collective.client.ClientContext;
import me.collective.client.utils.combat.BoundingBoxUtils;
import me.collective.client.managers.ModuleRegistry;
import me.collective.client.modules.exploit.ReachModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LiquidBlock.class)
public class LiquidBlockMixin {
   private static ReachModule reach;
   private static ReachModule reach() {
      if (reach == null) {
         reach = ClientContext.getModule(ReachModule.class);
      }
      return reach;
   }

   @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
   private void overrideLiquidSelectionShape(
      BlockState blockState, BlockGetter level, BlockPos blockPos, CollisionContext collisionContext, CallbackInfoReturnable<VoxelShape> callback
   ) {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null) {
         return;
      }
      if (reach().isToggled()
         && reach().liquidPlaceSetting.getValue()
         && (client.player.getMainHandItem().getItem() instanceof BlockItem || client.player.getOffhandItem().getItem() instanceof BlockItem)) {
         boolean hasAdjacentAir = false;

         for (Direction direction : Direction.values()) {
            if (level.getBlockState(blockPos.relative(direction)).isAir()) {
               hasAdjacentAir = true;
               break;
            }
         }

         AABB bounds = new AABB(blockPos);
         Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.mainCamera().position();
         Vec3 clampedPosition = BoundingBoxUtils.clampToBounds(
            cameraPosition, bounds
         );
         double distanceSquared = clampedPosition.distanceToSqr(cameraPosition);
         if (bounds.contains(cameraPosition)) {
            return;
         }

         if (distanceSquared < reach().modifierSetting.getValue() * reach().modifierSetting.getValue() && !hasAdjacentAir) {
            return;
         }

         callback.setReturnValue(Shapes.block());
         callback.cancel();
      }
   }

   @Unique
   private static void collective$bindLiquidBlockModules(ModuleRegistry moduleRegistry) {
      reach = (ReachModule)moduleRegistry.getModule(ReachModule.class);
   }
}
