package me.collective.mixin;

import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(ExplosionS2CPacket.class)
public interface ExplosionS2CPacketAccessor {
    @Mutable
    @Accessor("playerKnockback")
    void setPlayerKnockback(Optional<Vec3> playerKnockback);

    @Accessor("playerKnockback")
    Optional<Vec3> getPlayerKnockback();
}