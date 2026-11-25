package me.collective.mixin;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundSetEntityMotionPacket.class)
public interface ClientboundSetEntityMotionPacketAccessor {
   @Mutable
   @Accessor("xa")
   void collective$setXa(int xa);

   @Mutable
   @Accessor("ya")
   void collective$setYa(int ya);

   @Mutable
   @Accessor("za")
   void collective$setZa(int za);
}
