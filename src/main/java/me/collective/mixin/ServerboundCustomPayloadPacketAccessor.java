package me.collective.mixin;

import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundCustomPayloadPacket.class)
public interface ServerboundCustomPayloadPacketAccessor {
   @Mutable
   @Accessor("payload")
   void setCustomPayload(CustomPacketPayload payload);
}
