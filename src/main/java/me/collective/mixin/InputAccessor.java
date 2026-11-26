package me.collective.mixin;

import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Input.class)
public interface InputAccessor {
   @Mutable
   @Accessor("shift")
   void collective$setSneak(boolean shift);

   @Mutable
   @Accessor("sprint")
   void collective$setSprint(boolean sprint);

   @Mutable
   @Accessor("jump")
   void collective$setJump(boolean jump);

   @Mutable
   @Accessor("forward")
   void collective$setForward(boolean forward);

   @Mutable
   @Accessor("backward")
   void collective$setBack(boolean backward);

   @Mutable
   @Accessor("left")
   void collective$setLeft(boolean left);

   @Mutable
   @Accessor("right")
   void collective$setRight(boolean right);
}
