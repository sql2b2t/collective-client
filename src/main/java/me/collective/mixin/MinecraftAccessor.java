package me.collective.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("rightClickDelay")
    int collective$getRightClickDelay();

    @Accessor("rightClickDelay")
    void collective$setRightClickDelay(int delay);
}
