package me.collective.client.managers;

import me.collective.client.access.LocalPlayerTickAccess;

import me.collective.client.ClientAccess;

import me.collective.mixin.LivingEntityAccessor;
import net.minecraft.world.phys.Vec3;

public final class PlayerTickExecutor implements ClientAccess {
   private static boolean runningExtraTick;
   private PlayerTickExecutor() {
   }

   public static boolean isRunningExtraTick() {
      return runningExtraTick;
   }

   public static void runExtraTick() {
      runningExtraTick = true;
      int useItemRemainingTicks = ClientAccess.collectiveMinecraft.player.getUseItemRemainingTicks();
      float moveDistance = ClientAccess.collectiveMinecraft.player.moveDist;
      float walkAnimationSpeed = ClientAccess.collectiveMinecraft.player.walkAnimation.speed();
      float walkDistance = ClientAccess.collectiveMinecraft.player.avatarState().walkDist;
      float previousWalkDistance = ClientAccess.collectiveMinecraft.player.avatarState().walkDistO;
      float verticalBob = ClientAccess.collectiveMinecraft.player.yBob;
      float previousVerticalBob = ClientAccess.collectiveMinecraft.player.yBobO;
      float horizontalBob = ClientAccess.collectiveMinecraft.player.xBob;
      float previousHorizontalBob = ClientAccess.collectiveMinecraft.player.xBobO;
      Vec3 previousPosition = new Vec3(
         ClientAccess.collectiveMinecraft.player.xo,
         ClientAccess.collectiveMinecraft.player.yo,
         ClientAccess.collectiveMinecraft.player.zo
      );
      int attackStrengthTicks = ClientAccess.collectiveMinecraft.player.attackStrengthTicker;
      int swingTime = ClientAccess.collectiveMinecraft.player.swingTime;
      float previousAttackAnimation = ClientAccess.collectiveMinecraft.player.oAttackAnim;
      float attackAnimation = ClientAccess.collectiveMinecraft.player.attackAnim;
      LocalPlayerTickAccess baseTickPlayer = (LocalPlayerTickAccess)ClientAccess.collectiveMinecraft.player;
      baseTickPlayer.superTick();
      LocalPlayerTickAccess eventResetPlayer = (LocalPlayerTickAccess)ClientAccess.collectiveMinecraft.player;
      eventResetPlayer.resetEvent();
      ClientAccess.collectiveMinecraft.player.attackStrengthTicker = attackStrengthTicks;
      ((LivingEntityAccessor)ClientAccess.collectiveMinecraft.player)
         .setItemUseTimeLeft(useItemRemainingTicks);
      ClientAccess.collectiveMinecraft.player.moveDist = moveDistance;
      ClientAccess.collectiveMinecraft.player.walkAnimation.setSpeed(walkAnimationSpeed);
      ClientAccess.collectiveMinecraft.player.avatarState().walkDist = walkDistance;
      ClientAccess.collectiveMinecraft.player.avatarState().walkDistO = previousWalkDistance;
      ClientAccess.collectiveMinecraft.player.yBob = verticalBob;
      ClientAccess.collectiveMinecraft.player.yBobO = previousVerticalBob;
      ClientAccess.collectiveMinecraft.player.xBob = horizontalBob;
      ClientAccess.collectiveMinecraft.player.xBobO = previousHorizontalBob;
      ClientAccess.collectiveMinecraft.player.swingTime = swingTime;
      ClientAccess.collectiveMinecraft.player.oAttackAnim = previousAttackAnimation;
      ClientAccess.collectiveMinecraft.player.attackAnim = attackAnimation;
      LocalPlayerTickAccess movementPacketPlayer = (LocalPlayerTickAccess)ClientAccess.collectiveMinecraft.player;
      movementPacketPlayer.sendMovementPacketsWrapper();
      LocalPlayerTickAccess rotationResetPlayer = (LocalPlayerTickAccess)ClientAccess.collectiveMinecraft.player;
      rotationResetPlayer.resetRotations();
      ClientAccess.collectiveMinecraft.player.xo = previousPosition.x;
      ClientAccess.collectiveMinecraft.player.yo = previousPosition.y;
      ClientAccess.collectiveMinecraft.player.zo = previousPosition.z;
      runningExtraTick = false;
   }

}
