package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.PlacementModule
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.SpeedMineRebreakChoice
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.AnvilBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ConcretePowderBlock
import net.minecraft.world.level.block.DragonEggBlock
import net.minecraft.world.level.block.SandBlock
import java.util.ArrayList
import java.util.function.Predicate
import kotlin.math.ceil

class AutoDropModule : PlacementModule("AutoDrop", "Drops gravity blocks on your enemies.", ModuleCategory.COMBAT, "dropaura") {
    protected val targetRangeSetting: Setting<Float>
    protected val ignoreNakedsSetting: Setting<Boolean>
    protected val blocksSetting: Setting<Boolean>
    protected val anvilsSetting: Setting<Boolean>
    protected val sandSetting: Setting<Boolean>
    protected val gravelSetting: Setting<Boolean>
    protected val concreteSetting: Setting<Boolean>
    protected val dragonEggSetting: Setting<Boolean>
    private val completeSetting: Setting<Boolean>
    private var placingGravityBlock: Boolean = false

    init {
        this.targetRangeSetting = this.add(NumberSetting("TargetRange", 4.0f, 1.0f, 6.0f).withUnit("m"))
        this.ignoreNakedsSetting = this.add(BooleanSetting("IgnoreNakeds", false))
        this.blocksSetting = this.add(BooleanSetting("Blocks", false).markAsGroup())
        this.anvilsSetting = this.add(BooleanSetting("Anvils", true).dependsOn(this.blocksSetting))
        this.sandSetting = this.add(BooleanSetting("Sand", false).dependsOn(this.blocksSetting))
        this.gravelSetting = this.add(BooleanSetting("Gravel", false).dependsOn(this.blocksSetting))
        this.concreteSetting = this.add(BooleanSetting("Concrete", false).dependsOn(this.blocksSetting))
        this.dragonEggSetting = this.add(BooleanSetting("DragonEgg", false).dependsOn(this.blocksSetting))
        this.completeSetting = this.add(
            BooleanSetting("Complete", false).dependsOn(this.placementAutoDisableSetting),
            this.placementAutoDisableSetting
        )
        this.unregister(this.placementChorusSetting)
    }

    override fun findPlacementSwapSlot(): InventoryUtils.SwapSlot {
        return if (this.placingGravityBlock) {
            val gravityStackPredicate = Predicate<ItemStack> { this.isAllowedGravityStack(it) }
            InventoryUtils.findSwapSlot(gravityStackPredicate, false)
        } else {
            super.findPlacementSwapSlot()
        }
    }

    private fun selectDropTarget(): DropTargetPlan? {
        var nearestDistance = Double.MAX_VALUE
        var selectedTarget: DropTargetPlan? = null

        val level = ClientAccess.collectiveMinecraft.level ?: return null
        val localPlayer = ClientAccess.collectiveMinecraft.player ?: return null

        for (candidate in level.players()) {
            if (candidate.isAlive && localPlayer != candidate) {
                if (!ClientContext.socialRegistry.isFriendPlayer(candidate)) {
                    if (this.ignoreNakedsSetting.value && !PlayerStateUtils.hasArmor(candidate)) {
                        continue
                    }

                    if (!this.isPlayerInCobweb(candidate)) {
                        val dropPosition = this.getDropPosition(candidate)
                        val playerEyes = localPlayer.eyePosition
                        val candidatePosition = candidate.position()
                        val distance = playerEyes.distanceTo(candidatePosition)

                        val targetRange = this.targetRangeSetting.value.toDouble()
                        if (distance <= targetRange) {
                            val belowPosition = dropPosition.below()
                            val belowState = level.getBlockState(belowPosition)
                            if (belowState.canBeReplaced()) {
                                if (BlockInteractionUtils.canPlaceAt(dropPosition, false)) {
                                    if (!this.isInstantRebreakPosition(dropPosition)) {
                                        val directionCheck = BlockInteractionUtils.isStrictDirectionEnabled()
                                        val placementDirection = BlockInteractionUtils.findSupportDirection(
                                            dropPosition, directionCheck
                                        )

                                        val candidatePlan: DropTargetPlan? = if (placementDirection == null) {
                                            if (!this.placementAirPlaceSetting.value) {
                                                val cageDirection = this.findCageDirection(dropPosition)
                                                    ?: continue
                                                DropTargetPlan(candidate, DropPlacementMode.CAGE, cageDirection)
                                            } else {
                                                DropTargetPlan(candidate, DropPlacementMode.HEAD, null)
                                            }
                                        } else {
                                            DropTargetPlan(candidate, DropPlacementMode.HEAD, null)
                                        }

                                        if (distance < nearestDistance) {
                                            selectedTarget = candidatePlan
                                            nearestDistance = distance
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return selectedTarget
    }

    private fun isAllowedGravityStack(stack: ItemStack): Boolean {
        val item = stack.item
        if (item !is BlockItem) {
            return false
        }
        val block = item.block

        if (this.anvilsSetting.value && block is AnvilBlock) {
            return true
        }
        if (this.sandSetting.value && block is SandBlock) {
            return true
        }
        if (this.gravelSetting.value && block == Blocks.GRAVEL) {
            return true
        }
        if (this.concreteSetting.value && block is ConcretePowderBlock) {
            return true
        }
        return this.dragonEggSetting.value && block is DragonEggBlock
    }

    private fun isPlayerInCobweb(player: Player): Boolean {
        for (intersectingPosition in PlayerStateUtils.getOccupiedBlockPositions(player)) {
            if (WorldUtils.getBlock(intersectingPosition) == Blocks.COBWEB) {
                return true
            }
        }
        return false
    }

    private fun getDropPosition(entity: LivingEntity): BlockPos {
        val entityPositionSnapshot = PlayerStateUtils.getRoundedBlockPosition(entity)
        val height = entity.bbHeight.toDouble()
        return entityPositionSnapshot.above(ceil(height).toInt())
    }

    override fun getPlacementPositions(): MutableList<BlockPos> {
        val gravitySlot = InventoryUtils.findHotbarSlot { this.isAllowedGravityStack(it) }
        val positions = ArrayList<BlockPos>()
        this.placingGravityBlock = false

        val targetPlan = this.selectDropTarget()
        if (targetPlan == null) {
            if (this.completeSetting.value) {
                this.disable()
            }
            return positions
        }

        if (targetPlan.mode == DropPlacementMode.CAGE && gravitySlot != -1) {
            val cagePlayerSnapshot = targetPlan.player
            val cageOriginSnapshot = PlayerStateUtils.getRoundedBlockPosition(cagePlayerSnapshot)
            val cageDirection = targetPlan.cageDirection ?: Direction.UP
            val cageBaseSnapshot = cageOriginSnapshot.relative(cageDirection)

            for (heightOffset in 0 until 3) {
                positions.add(cageBaseSnapshot.above(heightOffset))
            }
        } else {
            val headPlayerSnapshot = targetPlan.player
            positions.add(this.getDropPosition(headPlayerSnapshot))
            this.placingGravityBlock = true
        }

        if (this.completeSetting.value && positions.isEmpty()) {
            this.disable()
        }

        return positions
    }

    private fun isInstantRebreakPosition(position: BlockPos): Boolean {
        val miningDamage = speedMineModule.miningProgress
        val damageSettingValue = speedMineModule.damageSetting.value
        val damageFraction = miningDamage / damageSettingValue

        if (speedMineModule.isToggled) {
            if (damageFraction >= 1.0f) {
                if (speedMineModule.rebreakSetting.value == SpeedMineRebreakChoice.INSTANT) {
                    if (position == speedMineModule.miningPos) {
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun findCageDirection(dropPosition: BlockPos): Direction? {
        val level = ClientAccess.collectiveMinecraft.level ?: return null
        val placeableDirections = BlockInteractionUtils.getStrictDirections(dropPosition)

        var nearestSupportDepth = 1337
        var selectedDirection: Direction? = null
        for (direction in Direction.entries) {
            if (!placeableDirections.contains(direction.opposite)) {
                if (BlockInteractionUtils.isStrictDirectionEnabled()) {
                    continue
                }
            }

            var supportDepth = -1
            for (depth in 0 until 3) {
                supportProbePosition.set(
                    dropPosition.x + direction.stepX,
                    dropPosition.y - depth,
                    dropPosition.z + direction.stepZ
                )

                val probeState = level.getBlockState(supportProbePosition)
                if (!probeState.canBeReplaced()) {
                    supportDepth = depth
                    break
                }
            }

            if (supportDepth != -1 && supportDepth < nearestSupportDepth) {
                nearestSupportDepth = supportDepth
                selectedDirection = direction
            }
        }

        return selectedDirection
    }

    companion object {
        lateinit var speedMineModule: SpeedMineModule

        private val supportProbePosition = BlockPos.MutableBlockPos()

        @JvmStatic
        fun bindModules(modules: ModuleRegistry) {
            speedMineModule = modules.getModule(SpeedMineModule::class.java) as SpeedMineModule
        }
    }
}
