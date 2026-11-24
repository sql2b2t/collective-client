package me.collective.client.modules.combat

import java.util.EnumMap
import java.util.HashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

internal class PistonCrystalStageTracker {
    enum class Kind { PISTON, CRYSTAL, POWER }

    private val pending: EnumMap<Kind, MutableMap<BlockPos, Long>> = EnumMap(
        mapOf(
            Kind.PISTON to HashMap(),
            Kind.CRYSTAL to HashMap(),
            Kind.POWER to HashMap()
        )
    )

    fun update() {
        val now = System.currentTimeMillis()
        pending.values.forEach { positions ->
            positions.values.removeIf { expiry -> now > expiry }
        }
    }

    fun reset() {
        pending.values.forEach { it.clear() }
    }

    fun markPiston(position: BlockPos, facing: Direction) {
        mark(Kind.PISTON, position.immutable())
        mark(Kind.PISTON, position.immutable().relative(facing))
    }

    fun markCrystal(position: BlockPos) {
        mark(Kind.CRYSTAL, position.immutable())
    }

    fun markPower(position: BlockPos) {
        mark(Kind.POWER, position.immutable())
    }

    fun isPending(kind: Kind, position: BlockPos): Boolean {
        return (pending[kind]?.getOrDefault(position, 0L) ?: 0L) > System.currentTimeMillis()
    }

    private fun mark(kind: Kind, position: BlockPos) {
        pending[kind]?.put(position, System.currentTimeMillis() + 300L)
    }
}
