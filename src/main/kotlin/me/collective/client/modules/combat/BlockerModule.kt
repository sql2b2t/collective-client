package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.PlacementModule
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.BlockerModeChoice
import me.collective.client.settings.options.BlockerTargetOffsets
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.BlockStateUtils
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import java.util.ArrayList
import java.util.HashMap

class BlockerModule : PlacementModule("Blocker", "Blocks certain offsets to prevent crystal damage.", ModuleCategory.COMBAT) {
    private val modeSetting: Setting<BlockerModeChoice>
    private val blockDamageSetting: Setting<Float>
    private val targetsSetting: Setting<Boolean>
    private val fishSetting: Setting<Boolean>
    private val surroundSetting: Setting<Boolean>
    private val cornersSetting: Setting<Boolean>
    private val faceSetting: Setting<Boolean>
    private val cevSetting: Setting<Boolean>
    private val extendedSetting: Setting<Boolean>
    private val destroyedPositionExpiries: MutableMap<BlockPos, Long> = HashMap()

    init {
        this.modeSetting = this.add(EnumSetting("Mode", BlockerModeChoice.DAMAGE))
        this.blockDamageSetting = this.add(NumberSetting("BlockDamage", 0.8f, 0.1f, 1.0f))
        this.targetsSetting = this.add(BooleanSetting("Targets", false).markAsGroup())
        this.fishSetting = this.add(BooleanSetting("Fish", false).dependsOn(this.targetsSetting))
        this.surroundSetting = this.add(
            BooleanSetting("Surround", true)
                .markVisibilityDependency().dependsOn(this.targetsSetting)
        )
        this.cornersSetting = this.add(BooleanSetting("Corners", false).dependsOn(this.targetsSetting, this.surroundSetting))
        this.faceSetting = this.add(BooleanSetting("Face", true).dependsOn(this.targetsSetting, this.surroundSetting))
        this.cevSetting = this.add(
            BooleanSetting("Cev", true)
                .markVisibilityDependency().dependsOn(this.targetsSetting)
        )
        this.extendedSetting = this.add(BooleanSetting("Extended", false).dependsOn(this.targetsSetting, this.cevSetting))

        this.placementJumpSetting.setDefaultValue(false)
        this.blockDamageSetting.addVisibilityPredicate {
            this.modeSetting.value == BlockerModeChoice.DAMAGE
        }
        this.ignoreMissingPlacementBlocks()
    }

    override fun getInfo(): String {
        return if (this.modeSetting.value == BlockerModeChoice.DAMAGE) {
            String.format("%.1f", this.blockDamageSetting.value)
        } else {
            this.modeSetting.value.getName()
        }
    }

    private fun shouldProtectBelow(): Boolean {
        if (!this.fishSetting.value) {
            return false
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val playerBounds = player.boundingBox
        val playerY = player.y
        val feetBounds = playerBounds.setMaxY(playerY)
        val minX = feetBounds.minX
        val minBlockX = Mth.floor(minX)
        val maxX = feetBounds.maxX
        val maxBlockX = Mth.floor(maxX)
        val minZ = feetBounds.minZ
        val minBlockZ = Mth.floor(minZ)
        val maxZ = feetBounds.maxZ
        val maxBlockZ = Mth.floor(maxZ)
        val occupiedColumns = (maxBlockX - minBlockX + 1) * (maxBlockZ - minBlockZ + 1)
        return occupiedColumns == 1
    }

    private fun getTargetOffsets(): List<BlockPos> {
        val offsets = ArrayList<BlockPos>()
        val player = ClientAccess.collectiveMinecraft.player ?: return offsets
        val surroundPositions = PlayerStateUtils.getSurroundPositions(player)

        if (this.surroundSetting.value) {
            offsets.addAll(surroundPositions)
        }

        if (this.cevSetting.value) {
            for (position in PlayerStateUtils.getOccupiedBlockPositions(player)) {
                offsets.add(position.above(2))
                offsets.add(position.above(3))
            }

            if (this.extendedSetting.value) {
                for (position in surroundPositions) {
                    offsets.add(position.above())
                }
            }
        }

        return offsets
    }

    @EventHandler
    private fun onLocalPlayerPreTick(event: LocalPlayerPreTickEvent) {
        synchronized(this.destroyedPositionExpiries) {
            this.destroyedPositionExpiries.values.removeIf { expiry -> System.currentTimeMillis() > expiry }
        }
    }

    override fun getPlacementPositions(): MutableList<BlockPos> {
        val player = ClientAccess.collectiveMinecraft.player ?: return mutableListOf()
        val playerBlockPosition = PlayerStateUtils.getLocalRoundedBlockPosition()
        val triggeredTargets = ArrayList<BlockPos>()

        for (targetPosition in this.getTargetOffsets()) {
            if (this.isTargetTriggered(targetPosition)) {
                triggeredTargets.add(targetPosition)
            }
        }

        val placements = ArrayList<BlockPos>()

        if (this.shouldProtectBelow()) {
            val belowPlayerPosition = playerBlockPosition.below()
            if (this.isTargetTriggered(belowPlayerPosition)) {
                for (belowSurroundPosition in PlayerStateUtils.getSurroundPositions(player)) {
                    val belowSidePosition = belowSurroundPosition.below()
                    placements.add(belowSidePosition)
                }
            }
        }

        for (triggeredTarget in triggeredTargets) {
            val playerPosition = player.position()
            for (surroundOffset in BlockerTargetOffsets.SURROUND.offsets) {
                if (triggeredTarget.y != playerPosition.y.toInt()) {
                    break
                }

                val localBlockPosition = BlockPos.containing(player.position())

                if (this.cornersSetting.value) {
                    if (triggeredTarget.offset(surroundOffset) != localBlockPosition) {
                        placements.add(triggeredTarget.offset(surroundOffset))
                    }
                } else {
                    if (triggeredTarget.subtract(localBlockPosition) == surroundOffset) {
                        placements.add(triggeredTarget.offset(surroundOffset))
                        break
                    }
                }
            }

            val targetY = triggeredTarget.y.toDouble()
            val playerY = player.y

            if (targetY == playerY + 3.0) {
                placements.add(triggeredTarget.below())
            } else {
                if (this.faceSetting.value) {
                    placements.add(triggeredTarget.above())
                }
            }
        }

        return placements
    }

    private fun isTargetTriggered(position: BlockPos): Boolean {
        return when (this.modeSetting.value) {
            BlockerModeChoice.DAMAGE -> {
                val blockDamage = ClientContext.blockBreakTracker.getBreakProgress(position)
                blockDamage >= this.blockDamageSetting.value
            }
            BlockerModeChoice.INSTANT -> {
                val level = ClientAccess.collectiveMinecraft.level ?: return false
                !BlockStateUtils.isBlastResistantUnbreakable(level.getBlockState(position))
            }
            BlockerModeChoice.DESTROY -> {
                synchronized(this.destroyedPositionExpiries) {
                    this.destroyedPositionExpiries.containsKey(position)
                }
            }
        }
    }

    @EventHandler
    private fun onInboundBlockUpdates(event: PacketEvent.Receive) {
        val packet = event.packet
        if (BlockUpdateBatch.isSupportedPacket(packet)) {
            val blockUpdates = BlockUpdateBatch(packet)
            for (update in blockUpdates) {
                val miningPosition = speedMineModule.miningPos
                val updatedStateSnapshot = update.state
                if (!updatedStateSnapshot.`is`(BlockTags.AIR)) {
                    // Одиночное обновление не должно обрывать обработку всего batch-пакета:
                    // иначе DESTROY-режим проморгивает разрушения после первого не-воздушного блока.
                    continue
                }

                if (miningPosition != null && miningPosition == update.position) {
                    continue
                }

                synchronized(this.destroyedPositionExpiries) {
                    val now = System.currentTimeMillis()
                    val expiry = now + 150L
                    this.destroyedPositionExpiries[update.position] = expiry
                }
            }
        }
    }

    companion object {
        lateinit var speedMineModule: SpeedMineModule

        @JvmStatic
        fun bindReferencedModules(registry: ModuleRegistry) {
            speedMineModule = registry.getModule(SpeedMineModule::class.java) as SpeedMineModule
        }
    }
}
