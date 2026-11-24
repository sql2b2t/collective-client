package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

enum class SpeedMineRenderModeChoice(private val displayName: String) : NamedEntry {
    PLAIN("Plain") {
        override fun getRenderBounds(bounds: AABB, progress: Float): AABB {
            return bounds
        }
    },
    IN("In") {
        override fun getRenderBounds(bounds: AABB, progress: Float): AABB {
            val remainingProgress = 1.0F - progress
            val width = bounds.getXsize()
            val absoluteWidth = Math.abs(width)
            val height = bounds.getYsize()
            val absoluteHeight = Math.abs(height)
            val depth = bounds.getZsize()
            val absoluteDepth = Math.abs(depth)
            val center: Vec3 = bounds.getCenter()
            return AABB(
                center.subtract(
                    remainingProgress * absoluteWidth / 2.0,
                    remainingProgress * absoluteHeight / 2.0,
                    remainingProgress * absoluteDepth / 2.0
                ),
                center.add(
                    remainingProgress * absoluteWidth / 2.0,
                    remainingProgress * absoluteHeight / 2.0,
                    remainingProgress * absoluteDepth / 2.0
                )
            )
        }
    },
    OUT("Out") {
        override fun getRenderBounds(bounds: AABB, progress: Float): AABB {
            val width = bounds.getXsize()
            val absoluteWidth = Math.abs(width)
            val height = bounds.getYsize()
            val absoluteHeight = Math.abs(height)
            val depth = bounds.getZsize()
            val absoluteDepth = Math.abs(depth)
            val center: Vec3 = bounds.getCenter()
            return AABB(
                center.subtract(
                    progress * absoluteWidth / 2.0,
                    progress * absoluteHeight / 2.0,
                    progress * absoluteDepth / 2.0
                ),
                center.add(
                    progress * absoluteWidth / 2.0,
                    progress * absoluteHeight / 2.0,
                    progress * absoluteDepth / 2.0
                )
            )
        }
    },
    IN_OUT("InOut") {
        override fun getRenderBounds(bounds: AABB, progress: Float): AABB {
            val clamped = Math.max(0.0F, Math.min(1.0F, progress))
            return OUT.getRenderBounds(bounds, Math.abs(2.0F * clamped - 1.0F))
        }
    };

    override fun getName(): String = displayName

    abstract fun getRenderBounds(bounds: AABB, progress: Float): AABB
}
