package me.collective.mixin;

import net.minecraft.network.protocol.game.ServerboundChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundChatPacket.class)
public interface ServerboundChatPacketAccessor {
   @Mutable
   @Accessor("message")
   void setChatMessage(String message);
}
