package me.collective.mixin;

import baritone.api.pathing.goals.GoalBlock;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.utils.world.WorldUtils;
import me.collective.client.modules.player.FreecamModule;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
@Mixin(targets = "baritone/command/defaults/ComeCommand", remap = false)
public class BaritoneComeCommandMixin {
   private static FreecamModule freecam;
   private static FreecamModule freecam() {
      if (freecam == null) {
         freecam = ClientContext.getModule(FreecamModule.class);
      }
      return freecam;
   }

   @ModifyArgs(
      method = "execute",
      at = @At(value = "INVOKE", target = "Lbaritone/api/process/ICustomGoalProcess;setGoalAndPath(Lbaritone/api/pathing/goals/Goal;)V"),
      remap = false
   )
   private void useFreecamGoal(Args args) {
      if (freecam().isToggled()) {
         BlockPos cameraPosition = ClientAccess.collectiveMinecraft.gameRenderer.getMainCamera().getBlockPosition();
         if (WorldUtils.isAir(cameraPosition)
            && WorldUtils.isAir(cameraPosition.below())
            && !WorldUtils.isAir(cameraPosition.below(2))) {
            args.set(0, new GoalBlock(cameraPosition.below()));
         } else {
            args.set(0, new GoalBlock(cameraPosition));
         }
      }
   }
}
