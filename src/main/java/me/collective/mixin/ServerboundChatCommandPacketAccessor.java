package me.collective.mixin;

import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundChatCommandPacket.class)
public interface ServerboundChatCommandPacketAccessor {
   @Mutable
   @Accessor("command")
   void setCommand(String command);
}
