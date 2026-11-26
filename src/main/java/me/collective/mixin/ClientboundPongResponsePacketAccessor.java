package me.collective.mixin;

import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundPongResponsePacket.class)
public interface ClientboundPongResponsePacketAccessor {
   @Mutable
   @Accessor("time")
   long collective$startTime();

   @Mutable
   @Accessor("time")
   void collective$startTime(long time);
}
