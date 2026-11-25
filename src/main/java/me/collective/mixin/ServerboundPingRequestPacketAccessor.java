package me.collective.mixin;

import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundPingRequestPacket.class)
public interface ServerboundPingRequestPacketAccessor {
   @Accessor("time")
   @Mutable
   void collective$startTime(long time);

   @Accessor("time")
   @Mutable
   long collective$startTime();
}
