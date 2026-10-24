package me.collective.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Смена сессии для AltManager. */
@Mixin(Minecraft.class)
public interface MixinMinecraftClient {
    @Mutable
    @Accessor("session")
    void setSession(User session);
}