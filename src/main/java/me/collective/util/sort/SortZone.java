package me.collective.util.sort;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Прямоугольная зона сортировки: две точки по диагонали задают рамку.
 * Роль зоны: SOURCE — откуда забирают, DESTINATION — куда кладут.
 */
public class SortZone {
    public String categoryId;
    public ZoneRole role;
    public String dimension;
    public int x1;
    public int y1;
    public int z1;
    public int x2;
    public int y2;
    public int z2;

    public SortZone() {
    }

    public SortZone(String categoryId, ZoneRole role, String dimension, BlockPos corner1, BlockPos corner2) {
        this.categoryId = categoryId;
        this.role = role;
        this.dimension = dimension;
        this.x1 = corner1.getX();
        this.y1 = corner1.getY();
        this.z1 = corner1.getZ();
        this.x2 = corner2.getX();
        this.y2 = corner2.getY();
        this.z2 = corner2.getZ();
    }

    public int minX() {
        return Math.min(x1, x2);
    }

    public int maxX() {
        return Math.max(x1, x2);
    }

    public int minZ() {
        return Math.min(z1, z2);
    }

    public int maxZ() {
        return Math.max(z1, z2);
    }

    public AABB box(int worldMinY, int worldMaxY) {
        return new AABB(minX(), worldMinY, minZ(), maxX() + 1.0, worldMaxY + 1.0, maxZ() + 1.0);
    }

    /** Внутри ли блок по горизонтали. Высоту игнорируем: стеш выше — тот же стеш. */
    public boolean contains(BlockPos pos) {
        return pos.getX() >= minX() && pos.getX() <= maxX() && pos.getZ() >= minZ() && pos.getZ() <= maxZ();
    }

    public String footprint() {
        return (maxX() - minX() + 1) + "x" + (maxZ() - minZ() + 1);
    }

    public String corners() {
        return minX() + ", " + minZ() + " -> " + maxX() + ", " + maxZ();
    }

    public enum ZoneRole {
        SOURCE,
        DESTINATION
    }
}