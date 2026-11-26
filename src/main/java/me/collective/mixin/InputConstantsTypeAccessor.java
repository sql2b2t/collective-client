package me.collective.mixin;

import com.mojang.blaze3d.platform.InputConstants.Type;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Type.class)
public interface InputConstantsTypeAccessor {
   @Accessor("map")
   Int2ObjectMap getKeyMap();
}
