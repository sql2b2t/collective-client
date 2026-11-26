package me.collective.mixin;

import java.util.Optional;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundExplodePacket.class)
public interface ClientboundExplodePacketAccessor {
   @Mutable
   @Accessor("playerKnockback")
   void setVelocity(Optional playerKnockback);
}
