package me.collective.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
   @Accessor("lastOnGround")
   boolean lastOnGround();

   @Accessor("lastOnGround")
   void setLastOnGround(boolean lastOnGround);

   @Accessor("xLast")
   double lastX();

   @Accessor("xLast")
   void setLastX(double lastX);

   @Accessor("yLast")
   double lastY();

   @Accessor("yLast")
   void setLastY(double lastY);

   @Accessor("zLast")
   double lastZ();

   @Accessor("zLast")
   void setLastZ(double lastZ);

   @Accessor("yRotLast")
   float lastYaw();

   @Accessor("yRotLast")
   void setLastYRot(float lastYRot);

   @Accessor("xRotLast")
   float lastXRot();

   @Accessor("xRotLast")
   void setLastXRot(float lastXRot);

   @Accessor("wasSprinting")
   boolean wasSprinting();

   @Accessor("wasSprinting")
   void collective$setLastSprinting(boolean wasSprinting);

   @Invoker("modifyInput")
   Vec2 collective$applyMovementSpeedFactors(Vec2 input);

   @Accessor("positionReminder")
   void collective$setTicksSinceLastPositionPacketSent(int positionReminder);

   @Accessor("positionReminder")
   int collective$getTicksSinceLastPositionPacketSent();

   @Accessor("lastSentInput")
   void collective$setLastPlayerInput(Input lastSentInput);
}
