package me.collective.module.highway;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;

/**
 * Изменяемая позиция блока (порт Meteor MBlockPos).
 * getBlockPos() всегда возвращает свежую копию — безопасно хранить.
 */
public class MBlockPos {
    public int x;
    public int y;
    public int z;

    public MBlockPos() {
    }

    public MBlockPos set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public MBlockPos set(MBlockPos pos) {
        return set(pos.x, pos.y, pos.z);
    }

    public MBlockPos set(BlockPos pos) {
        return set(pos.getX(), pos.getY(), pos.getZ());
    }

    public MBlockPos set(Entity entity) {
        return set(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ());
    }

    public MBlockPos coerceBlockLevel(Entity entity) {
        return set(entity.getBlockX(), (int) Math.round(entity.getY()), entity.getBlockZ());
    }

    public MBlockPos offset(HorizontalDirection dir, int amount) {
        x += dir.offsetX * amount;
        z += dir.offsetZ * amount;
        return this;
    }

    public MBlockPos offset(HorizontalDirection dir) {
        return offset(dir, 1);
    }

    public MBlockPos offset(net.minecraft.util.math.Direction dir, int amount) {
        x += dir.getOffsetX() * amount;
        y += dir.getOffsetY() * amount;
        z += dir.getOffsetZ() * amount;
        return this;
    }

    public MBlockPos offset(net.minecraft.util.math.Direction dir) {
        return offset(dir, 1);
    }

    public MBlockPos add(int x, int y, int z) {
        this.x += x;
        this.y += y;
        this.z += z;
        return this;
    }

    public BlockPos getBlockPos() {
        return new BlockPos(x, y, z);
    }

    public BlockState getState() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.world == null) return Blocks.AIR.getDefaultState();
        return mc.world.getBlockState(getBlockPos());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof MBlockPos other) {
            return x == other.x && y == other.y && z == other.z;
        }
        if (o instanceof BlockPos other) {
            return x == other.getX() && y == other.getY() && z == other.getZ();
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + z;
        return result;
    }

    @Override
    public String toString() {
        return x + ", " + y + ", " + z;
    }
}