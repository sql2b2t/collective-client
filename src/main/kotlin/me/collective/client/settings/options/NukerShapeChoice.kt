package me.collective.client.settings.options

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.utils.world.WorldUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.player.NukerModule
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.settings.NamedEntry

import java.util.Collections
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.BlockHitResult

enum class NukerShapeChoice(private val displayName: String) : NamedEntry {
    SPHERE("Sphere") {
        override fun collectPositions(nukerModule: NukerModule): List<*> {
            val playerForPosition = ClientAccess.collectiveMinecraft.player!!
            val positionSnapshot = playerForPosition.position()
            val rangeSettingRef = nukerModule.rangeSetting
            val configuredRange = rangeSettingRef.getValue()
            val rangeSnapshot = configuredRange
            return ArrayList(WorldUtils.collectSphereBlocks(positionSnapshot, rangeSnapshot, true))
        }
    },
    TUNNEL("Tunnel") {
        override fun collectPositions(nukerModule: NukerModule): List<*> {
            val positions = ArrayList<BlockPos>()
            val heightSettingRef = nukerModule.heightSetting
            val height: Double
            if (heightSettingRef.isAtMinimum()) {
                val playerForHeight = ClientAccess.collectiveMinecraft.player!!
                val playerHeight = playerForHeight.getBbHeight()
                height = Math.ceil(playerHeight.toDouble())
            } else {
                val configuredHeightSetting = nukerModule.heightSetting
                val configuredHeight = configuredHeightSetting.getValue()
                height = configuredHeight.toDouble()
            }

            var distance = 0

            while (true) {
                val rangeSettingRef = nukerModule.rangeSetting
                val configuredRange = rangeSettingRef.getValue()
                val range = configuredRange.toDouble()
                if (!(distance <= Math.ceil(range))) {
                    return positions
                }

                var heightOffset = 0
                while (heightOffset < height) {
                    val playerForPosition = ClientAccess.collectiveMinecraft.player!!
                    var position = playerForPosition.blockPosition()
                    position = position.above(heightOffset)
                    val playerForDirection = ClientAccess.collectiveMinecraft.player!!
                    val directionSnapshot = playerForDirection.getDirection()
                    position = position.relative(directionSnapshot, distance)
                    positions.add(position)

                    heightOffset++
                }

                distance++
            }
        }
    },
    QUEUE("Queue") {
        override fun collectPositions(nukerModule: NukerModule): List<*> {
            val queueSnapshot = nukerModule.getBlockQueue()
            val extraBreakSettingRef = NukerShapeChoice.speedMineModule!!.extraBreakSetting
            val extraBreakEnabled = extraBreakSettingRef.getValue()
            val extraBreakFlag = extraBreakEnabled

            return queueSnapshot.getNextPositions(extraBreakFlag)
        }
    },
    CROSSHAIR("Crosshair") {
        override fun collectPositions(nukerModule: NukerModule): List<*> {
            val hitResultSnapshot = ClientAccess.collectiveMinecraft.hitResult
            if (hitResultSnapshot is BlockHitResult) {
                return ArrayList(Collections.singletonList(hitResultSnapshot.getBlockPos()))
            } else {
                return Collections.emptyList<Any>()
            }
        }
    };

    override fun getName(): String = displayName

    abstract fun collectPositions(nukerModule: NukerModule): List<*>

    companion object {
        @JvmField
        var speedMineModule: SpeedMineModule? = null

        @JvmStatic
        fun bindModules(modules: ModuleRegistry) {
            speedMineModule = modules.getModuleOrNull(SpeedMineModule::class.java)
        }
    }
}
