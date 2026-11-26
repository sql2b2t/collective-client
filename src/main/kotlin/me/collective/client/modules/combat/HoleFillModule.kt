package me.collective.client.modules.combat

import net.minecraft.world.phys.Vec3
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.BlockUpdateAppliedEvent
import me.collective.client.events.EventHandler
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.PlacementModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.world.BlockStateUtils
import me.collective.client.utils.world.HoleInfo
import me.collective.mixin.BlockBehaviourAccessor
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import java.util.ArrayList
import java.util.Comparator
import java.util.function.Predicate

class HoleFillModule : PlacementModule("HoleFill", "Blocks safe holes nearby with obsidian.", ModuleCategory.COMBAT) {
    val blocksSetting: Setting<Set<Block>>
    private val sequentialSetting: Setting<Boolean>
    val modeSetting: Setting<ModeChoice>
    val rangeSetting: Setting<Float>
    val verticalRangeSetting: Setting<Float>
    val doublesSetting: Setting<Boolean>
    val onlySafeSetting: Setting<Boolean>
    val selfSetting: Setting<Boolean>
    val extrapolationSetting: Setting<Boolean>
    val ticksSetting: Setting<Int>
    val smartSetting: Setting<Boolean>
    val smartRangeSetting: Setting<Float>
    val selfSafetySetting: Setting<Boolean>
    val ignoreNakedsSetting: Setting<Boolean>
    val plainPolicy: PlainHoleFillPolicy
    val smartPolicy: PlainHoleFillPolicy
    var candidateCount: Int = 0
        protected set

    init {
        this.blocksSetting = this.add(
            RegistrySetSetting("Blocks", BuiltInRegistries.BLOCK, Blocks.OBSIDIAN, Blocks.ENDER_CHEST)
        )
        this.sequentialSetting = this.add(BooleanSetting("Sequential", false))
        this.modeSetting = this.add(EnumSetting("Mode", ModeChoice.PLAIN))
        this.rangeSetting = this.add(NumberSetting("Range", 4.0f, 1.0f, 6.5f).withUnit("m"))
        this.verticalRangeSetting = this.add(NumberSetting("VerticalRange", 3.0f, 1.0f, 6.0f).withUnit("m"))
        this.doublesSetting = this.add(BooleanSetting("Doubles", false))
        this.onlySafeSetting = this.add(BooleanSetting("OnlySafe", false))
        this.selfSetting = this.add(BooleanSetting("Self", true))
        this.extrapolationSetting = this.add(BooleanSetting("Extrapolation", true).markVisibilityDependency())

        val ticksVisibility = Predicate<Any?> { this.extrapolationSetting.isExpanded }
        this.ticksSetting = this.add(
            NumberSetting("Ticks", 4, 0, 8, ticksVisibility)
                .setBoundaryLabel("Auto", SettingLabelCondition.MIN)
        )

        val smartVisibility = Predicate<Any?> { this.modeSetting.value == ModeChoice.SMART }
        this.smartSetting = this.add(BooleanSetting("Smart", false, smartVisibility).markAsGroup())
        this.smartRangeSetting = this.add(NumberSetting("SmartRange", 1.5f, 0.5f, 4.0f).dependsOn(this.smartSetting))
        this.selfSafetySetting = this.add(BooleanSetting("SelfSafety", false).dependsOn(this.smartSetting))
        this.ignoreNakedsSetting = this.add(BooleanSetting("IgnoreNakeds", false).dependsOn(this.smartSetting))

        this.plainPolicy = PlainHoleFillPolicy(this)
        this.smartPolicy = SmartHoleFillPolicy(this)

        this.unregister(this.placementAirPlaceSetting)
        this.unregister(this.placementChorusSetting)
        this.unregister(this.placementAttackSetting)
        this.placementAttackSetting.value = false
        this.placementJumpSetting.setDefaultValue(false)

        this.selfSetting.addVisibilityPredicate {
            for (selectedBlock in this.blocksSetting.value) {
                if (!(selectedBlock as BlockBehaviourAccessor).isCollidable) {
                    return@addVisibilityPredicate true
                }
            }
            false
        }
    }

    private fun getStackBlock(stack: ItemStack): Block {
        val item = stack.item
        return if (item is BlockItem) {
            item.block
        } else {
            Blocks.VOID_AIR
        }
    }

    override fun getPlacementRotationPriority(): Int {
        return 150
    }

    override fun findPlacementSwapSlot(): InventoryUtils.SwapSlot {
        if (this.blocksSetting.value.isEmpty()) {
            return super.findPlacementSwapSlot()
        }

        val blockPredicate = Predicate<ItemStack> { stack ->
            this.blocksSetting.value.contains(this.getStackBlock(stack))
        }

        return InventoryUtils.findSwapSlot(blockPredicate, false)
    }

    override fun getInfo(): String {
        return this.candidateCount.toString()
    }

    override fun runPlacementCycle() {
        if (!ClientContext.holeManager.isInHole) {
            if (this.onlySafeSetting.value) {
                return
            }
        }

        super.runPlacementCycle()
    }

    @EventHandler
    private fun onSequentialPlacement(event: BlockUpdateAppliedEvent) {
        if (this.sequentialSetting.value) {
            this.runPlacementCycle()
        }
    }

    override fun getPlacementPositions(): MutableList<BlockPos> {
        val positions = ArrayList<BlockPos>()

        val level = ClientAccess.collectiveMinecraft.level ?: return positions

        for (hole in (ClientContext.holeManager.holes as List<HoleInfo>)) {
            if (hole.trapped) {
                continue
            }

            if (!this.modeSetting.value.getPolicy(this).shouldFill(hole)) {
                continue
            }

            if (!this.selfSetting.value || !this.selfSetting.isVisible) {
                val localCheckPosition = hole.pos
                if (localCheckPosition == PlayerStateUtils.getLocalRoundedBlockPosition()) {
                    continue
                }
            }

            var useAbovePosition = true
            val holeOrigin = hole.pos
            val aboveHole = holeOrigin.above()

            for (direction in Direction.entries) {
                val axis = direction.axis
                if (!axis.isVertical) {
                    val sidePos = aboveHole.relative(direction)
                    val sideState = level.getBlockState(sidePos)
                    var resistantSide = BlockStateUtils.isBlastResistantBreakable(sideState)
                    if (!resistantSide) {
                        val sideStateSnapshot = level.getBlockState(sidePos)
                        resistantSide = BlockStateUtils.isBlastResistantUnbreakable(sideStateSnapshot)
                    }

                    val aboveStateSnapshot = level.getBlockState(aboveHole)
                    if (BlockStateUtils.isBlocking(aboveStateSnapshot) || !resistantSide) {
                        useAbovePosition = false
                        break
                    }
                }
            }

            if (useAbovePosition) {
                positions.add(aboveHole)
            } else {
                positions.add(hole.pos)
            }
        }

        this.candidateCount = positions.size
        val player = ClientAccess.collectiveMinecraft.player
        if (player != null) {
            val eyeSnapshot = player.eyePosition
            positions.sortWith(Comparator.comparingDouble { sortPos ->
                eyeSnapshot.distanceTo(Vec3.atCenterOf(sortPos))
            })
        }
        return positions
    }

    enum class ModeChoice(private val displayName: String) : NamedEntry {
        PLAIN("Plain"),
        SMART("Smart");

        override fun getName(): String {
            return this.displayName
        }

        fun getPolicy(holeFillModule: HoleFillModule): PlainHoleFillPolicy {
            return if (this == PLAIN) {
                holeFillModule.plainPolicy
            } else {
                holeFillModule.smartPolicy
            }
        }
    }
}
