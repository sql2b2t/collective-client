package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.RotationRequestEvent
import me.collective.client.managers.InventoryStateManager
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.exploit.MultiTaskModule
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.render.FeatureBoxRenderer
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.inventory.ItemUtils
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import java.awt.Color
import java.util.function.Predicate

class AntiFeetPlaceModule : Module("AntiFeetPlace", createDescription(), ModuleCategory.COMBAT) {
    private val modeSetting: Setting<ModeChoice>
    private val delaySetting: Setting<Int>
    private val airPlaceSetting: Setting<Boolean>
    private val raytraceSetting: Setting<Boolean>
    private val rotateSetting: Setting<Boolean>
    val renderSetting: Setting<Boolean>
    private val fillSetting: Setting<Color>
    private val outlineSetting: Setting<Color>
    private val lineWidthSetting: Setting<Float>
    private val fadeSetting: Setting<Boolean>
    private val fadeTimeSetting: Setting<Float>
    private val placementTimer: ElapsedTimer

    init {
        this.modeSetting = this.add(EnumSetting("Mode", ModeChoice.GLASS))
        this.delaySetting = this.add(NumberSetting("Delay", 0, 0, 500))
        this.airPlaceSetting = this.add(BooleanSetting("AirPlace", false))
        this.raytraceSetting = this.add(BooleanSetting("Raytrace", false))
        this.rotateSetting = this.add(BooleanSetting("Rotate", false))
        this.renderSetting = this.add(BooleanSetting("Render", false).markVisibilityDependency())

        val fillVisibility = Predicate<Any?> { this.renderSetting.isExpanded }
        this.fillSetting = this.add(ColorSetting("Fill", Color(146643455, true), fillVisibility))

        val outlineVisibility = Predicate<Any?> { this.renderSetting.isExpanded }
        this.outlineSetting = this.add(ColorSetting("Outline", Color(1539152383, true), outlineVisibility))

        this.lineWidthSetting = this.add(NumberSetting("LineWidth", 1.0f, 0.1f, 3.0f).dependsOn(this.renderSetting))
        this.fadeSetting = this.add(BooleanSetting("Fade", true).dependsOn(this.renderSetting))
        this.fadeTimeSetting = this.add(
            NumberSetting("FadeTime", 1.0f, 0.1f, 1.0f)
                .withUnit("s").dependsOn(this.renderSetting, this.fadeSetting)
        )
        this.placementTimer = ElapsedTimer()

        val renderConfiguration = FeatureBoxRenderer.RenderConfiguration(
            this, this.fillSetting, this.outlineSetting, this.lineWidthSetting, this.fadeTimeSetting,
            { true }, this.fadeSetting, 450
        )
        ClientContext.featureBoxRenderer.register(renderConfiguration)
    }

    private fun shouldPauseReplacement(): Boolean {
        val miningPosition = if (speedMine.miningPos != null) {
            speedMine.miningPos
        } else {
            speedMine.rebreakPos
        }

        if (!speedMine.isToggled || miningPosition == null) {
            return true
        }

        val miningProgress = speedMine.renderProgress
        if (miningProgress < 0.95 && !WorldUtils.isAir(miningPosition)) {
            return true
        }

        if (multiTask.shouldPauseModule(this)) {
            return true
        }

        val delayMillis = this.delaySetting.value.toLong()
        if (!this.placementTimer.hasElapsed(delayMillis)) {
            return true
        }

        if (!BlockInteractionUtils.canPlayerInteract() || PlayerStateUtils.isFallFlying()) {
            return true
        }

        return OffhandModule.isRecentSwapWindowActive() || SpeedMineModule.isSwapPending()
    }

    private fun replaceMinedBlock() {
        if (!this.shouldPauseReplacement()) {
            val miningPosition = if (speedMine.miningPos != null) {
                speedMine.miningPos
            } else {
                speedMine.rebreakPos
            } ?: return

            val placementBounds = AABB(miningPosition)

            if (!this.hasNoEligibleEnemy(miningPosition)) {
                if (!BlockInteractionUtils.isOutsidePlacementBounds(miningPosition)) {
                    if (!BlockInteractionUtils.hasBlockingEntity(placementBounds, true, true)) {
                        val belowPositionSnapshot = miningPosition.below()
                        if (!WorldUtils.isBedrockOrObsidian(belowPositionSnapshot)) {
                            return
                        }

                        val strictDirectionEnabled = BlockInteractionUtils.isStrictDirectionEnabled()
                        val raytraceFlag = this.raytraceSetting.value
                        val supportDirection = BlockInteractionUtils.findSupportDirection(
                            miningPosition, strictDirectionEnabled, raytraceFlag
                        )

                        if (supportDirection == null && !this.airPlaceSetting.value) {
                            return
                        }

                        val previousSlot = InventoryUtils.getSelectedSlot()
                        val selectedItem = this.selectPlacementItem()
                        InventoryUtils.runWithSwap(previousSlot, selectedItem) {
                            val interactionHand = selectedItem.hand
                            this.placeReplacement(miningPosition, supportDirection, interactionHand)
                        }
                    }
                }
            }
        }
    }

    private fun placeReplacement(
        position: BlockPos,
        supportDirection: Direction?,
        hand: InteractionHand
    ) {
        this.placementTimer.reset()

        val airPlaceEnabled = this.airPlaceSetting.value
        BlockInteractionUtils.sendPlacement(position, null, supportDirection, airPlaceEnabled, hand)
        ClientContext.swingManager.swing(InteractionHand.MAIN_HAND, SwingAction.INTERACT)

        if (this.renderSetting.value) {
            ClientContext.featureBoxRenderer.addBox(this, position)
        }

        if (this.rotateSetting.value) {
            ClientContext.rotationManager.requestRotation(RotationManager.getRotationsToBlock(position), 350)
        }
    }

    private fun hasNoEligibleEnemy(position: BlockPos): Boolean {
        val level = ClientAccess.collectiveMinecraft.level ?: return true
        val localPlayer = ClientAccess.collectiveMinecraft.player

        for (target in level.players()) {
            if (target != localPlayer) {
                if (!ClientContext.socialRegistry.isFriendPlayer(target)) {
                    if (!target.isDeadOrDying && target.isAlive) {
                        val distanceSquared = target.distanceToSqr(position.x.toDouble(), position.y.toDouble(), position.z.toDouble())
                        if (distanceSquared <= 36.0) {
                            if (PlayerStateUtils.hasArmor(target)) {
                                val candidatePositions = PlayerStateUtils.getSurroundPositions(target)
                                if (candidatePositions.contains(position)) {
                                    var hasNoAdjacentAir = true
                                    for (feetPosition in PlayerStateUtils.getOccupiedBlockPositions(target)) {
                                        val state = level.getBlockState(feetPosition)
                                        if (state.isAir) {
                                            if (feetPosition.distManhattan(position) == 1) {
                                                hasNoAdjacentAir = false
                                                break
                                            }
                                        }
                                    }

                                    if (!hasNoAdjacentAir) {
                                        return false
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return true
    }

    private fun selectPlacementItem(): InventoryUtils.SwapSlot {
        val mode = this.modeSetting.value
        val hotbarSlot = InventoryUtils.findHotbarSlot(mode.itemPredicate)

        if (this.isHoldingPlacementItemOffhand()) {
            return InventoryUtils.SwapSlot(
                InventoryUtils.getSelectedSlot(),
                true,
                InteractionHand.OFF_HAND
            )
        }

        if (hotbarSlot == -1) {
            if (!InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 3)) {
                return InventoryUtils.SwapSlot(
                    InventoryUtils.findInventorySlot(mode.itemPredicate, true),
                    false,
                    InteractionHand.MAIN_HAND
                )
            }
        }

        return InventoryUtils.SwapSlot(hotbarSlot, true, InteractionHand.MAIN_HAND)
    }

    private fun isHoldingPlacementItemOffhand(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        return this.modeSetting.value.itemPredicate.test(player.offhandItem)
    }

    @EventHandler(priority = -100)
    fun onPlayerUpdate(event: RotationRequestEvent) {
        this.replaceMinedBlock()
    }

    private enum class ModeChoice(
        private val displayName: String,
        val itemPredicate: Predicate<ItemStack>
    ) : NamedEntry {
        GLASS("Glass", Predicate { stack -> ItemUtils.isGlass(stack.item) }),
        ICE("Ice", Predicate { stack ->
            if (!stack.`is`(Items.PACKED_ICE)) {
                val dimension = ClientContext.worldStateManager.dimension
                if (!dimension.isNether) {
                    stack.`is`(Items.ICE)
                } else {
                    false
                }
            } else {
                true
            }
        }),
        ANVIL("Anvil", Predicate { stack -> stack.`is`(Items.ANVIL) });

        override fun getName(): String = this.displayName
    }

    companion object {
        lateinit var speedMine: SpeedMineModule
        lateinit var multiTask: MultiTaskModule

        @JvmStatic
        fun bindModules(modules: ModuleRegistry) {
            speedMine = modules.getModule(SpeedMineModule::class.java) as SpeedMineModule
            multiTask = modules.getModule(MultiTaskModule::class.java) as MultiTaskModule
        }

        private fun createDescription(): String {
            val warningColor = ChatFormatting.RED.toString()
            return "Tries to replace the enemy's feet block with a faster-breaking one.\n${warningColor}Requires SpeedMine module enabled."
        }
    }
}
