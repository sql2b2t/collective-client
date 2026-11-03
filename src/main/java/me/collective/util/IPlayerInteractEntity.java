package me.collective.util;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;

public interface IPlayerInteractEntity {
    PlayerInteractEntityC2SPacket.InteractType collective$getType();
    Entity collective$getEntity();

    static PlayerInteractEntityC2SPacket createAttack(int entityId, boolean sneaking) {
        io.netty.buffer.ByteBuf byteBuf = io.netty.buffer.Unpooled.buffer();
        net.minecraft.network.PacketByteBuf buf = new net.minecraft.network.PacketByteBuf(byteBuf);
        buf.writeVarInt(entityId);
        buf.writeVarInt(1); // ATTACK ordinal
        buf.writeBoolean(sneaking);
        return PlayerInteractEntityC2SPacket.CODEC.decode(buf);
    }
}
