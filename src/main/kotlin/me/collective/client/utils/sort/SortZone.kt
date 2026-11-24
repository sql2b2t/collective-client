package me.collective.client.utils.sort

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB

/**
 * Прямоугольная зона сортировки: две точки по диагонали задают рамку.
 * Роль зоны: SOURCE — откуда забирают, DESTINATION — куда кладут.
 */
class SortZone(categoryId: String, role: ZoneRole, dimension: String, corner1: BlockPos, corner2: BlockPos) {
    @JvmField var categoryId: String = categoryId
    @JvmField var role: ZoneRole = role
    @JvmField var dimension: String = dimension
    @JvmField var x1: Int = corner1.x
    @JvmField var y1: Int = corner1.y
    @JvmField var z1: Int = corner1.z
    @JvmField var x2: Int = corner2.x
    @JvmField var y2: Int = corner2.y
    @JvmField var z2: Int = corner2.z

    /** Пустой конструктор для Gson при чтении из JSON. */
    constructor() : this("", ZoneRole.SOURCE, "unknown", BlockPos.ZERO, BlockPos.ZERO)

    fun minX(): Int = minOf(x1, x2)

    fun maxX(): Int = maxOf(x1, x2)

    fun minZ(): Int = minOf(z1, z2)

    fun maxZ(): Int = maxOf(z1, z2)

    fun box(worldMinY: Int, worldMaxY: Int): AABB {
        return AABB(minX().toDouble(), worldMinY.toDouble(), minZ().toDouble(), maxX() + 1.0, worldMaxY + 1.0, maxZ() + 1.0)
    }

    /** Внутри ли блок по горизонтали. Высоту игнорируем: стеш выше — тот же стеш. */
    fun contains(pos: BlockPos): Boolean {
        return pos.x >= minX() && pos.x <= maxX() && pos.z >= minZ() && pos.z <= maxZ()
    }

    fun footprint(): String {
        return (maxX() - minX() + 1).toString() + "x" + (maxZ() - minZ() + 1)
    }

    fun corners(): String {
        return minX().toString() + ", " + minZ() + " -> " + maxX() + ", " + maxZ()
    }

    enum class ZoneRole {
        SOURCE,
        DESTINATION
    }
}
