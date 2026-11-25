package me.collective.mixin;

import java.util.OptionalLong;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DimensionType.class)
public interface DimensionTypeAccessor {
   @Accessor("fixedTime")
   @Mutable
   void collective$setFixedTimeRaw(OptionalLong fixedTime);
}
