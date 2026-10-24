package me.collective.util.sort;

import net.minecraft.core.BlockPos;

/** Метка: «этот сундук — дом для такой-то категории». */
public class ChestPin {
    public int x;
    public int y;
    public int z;
    public String dimension;
    public String groupKey;

    public ChestPin() {
    }

    public ChestPin(BlockPos pos, String dimension, String groupKey) {
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.dimension = dimension;
        this.groupKey = groupKey;
    }

    public BlockPos pos() {
        return new BlockPos(x, y, z);
    }

    public boolean matches(BlockPos pos, String dim) {
        return x == pos.getX() && y == pos.getY() && z == pos.getZ() && dimension.equals(dim);
    }
}