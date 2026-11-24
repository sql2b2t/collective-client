package me.collective.mixin;

import net.minecraft.world.entity.PositionMoveRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PositionMoveRotation.class)
public interface PositionMoveRotationAccessor {
   @Mutable
   @Accessor("yRot")
   void collective$setYaw(float yRot);

   @Mutable
   @Accessor("xRot")
   void collective$setPitch(float xRot);
}
