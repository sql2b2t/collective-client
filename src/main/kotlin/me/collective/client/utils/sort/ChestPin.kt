package me.collective.client.utils.sort

import net.minecraft.core.BlockPos

/** Метка: «этот сундук — дом для такой-то категории». */
class ChestPin(pos: BlockPos, dimension: String, groupKey: String) {
    @JvmField var x: Int = pos.x
    @JvmField var y: Int = pos.y
    @JvmField var z: Int = pos.z
    @JvmField var dimension: String = dimension
    @JvmField var groupKey: String = groupKey

    /** Пустой конструктор для Gson при чтении из JSON. */
    constructor() : this(BlockPos.ZERO, "", "")

    fun pos(): BlockPos = BlockPos(x, y, z)

    fun matches(pos: BlockPos, dim: String): Boolean {
        return x == pos.x && y == pos.y && z == pos.z && dimension == dim
    }
}
