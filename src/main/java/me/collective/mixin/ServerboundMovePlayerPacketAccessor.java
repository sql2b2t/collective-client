package me.collective.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMovePlayerPacket.class)
public interface ServerboundMovePlayerPacketAccessor {
   @Mutable
   @Accessor("onGround")
   void setOnGround(boolean onGround);

   @Accessor("hasPos")
   boolean isChangePosition();

   @Mutable
   @Accessor("y")
   void setY(double y);

   @Mutable
   @Accessor("x")
   void setX(double x);

   @Mutable
   @Accessor("z")
   void setZ(double z);

   @Mutable
   @Accessor("yRot")
   void setYaw(float yRot);

   @Mutable
   @Accessor("xRot")
   void setPitch(float xRot);
}
