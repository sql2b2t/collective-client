package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.RotationRequestEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.exploit.MultiTaskModule
import me.collective.client.render.FeatureBoxRenderer
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.AntiPhaseMaterialChoice
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BannerItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.AbstractBannerBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import java.awt.Color

class AntiPhaseModule : Module("AntiPhase", "Prevents your enemies from pearl-phasing into blocks.", ModuleCategory.COMBAT) {
    internal val materialSetting: Setting<AntiPhaseMaterialChoice> = add(EnumSetting("Material", AntiPhaseMaterialChoice.LADDER))

    @JvmField
    val bannerSetting: Setting<Boolean> = add(BooleanSetting("Banner", false) { materialSetting.value == AntiPhaseMaterialChoice.ITEMFRAME })

    private val rangeSetting: Setting<Float> = add(NumberSetting("Range", 4.5f, 0.1f, 6.0f))
    protected val delaySetting: Setting<Int> = add(NumberSetting("Delay", 100, 50, 250).withUnit("ms"))
    protected val blocksPerTickSetting: Setting<Int> = add(NumberSetting("BPT", 6, 1, 6))
    protected val rotateSetting: Setting<Boolean> = add(BooleanSetting("Rotate", false))
    protected val noCrawlSetting: Setting<Boolean> = add(BooleanSetting("NoCrawl", false))
    protected val smartSetting: Setting<Boolean> = add(BooleanSetting("Smart", false))
    protected val renderSetting: Setting<Boolean> = add(BooleanSetting("Render", false).markVisibilityDependency())
    protected val fillSetting: Setting<Color> = add(ColorSetting("Fill", Color(146643455, true)) { renderSetting.isExpanded })
    protected val outlineSetting: Setting<Color> = add(ColorSetting("Outline", Color(1539152383, true)) { renderSetting.isExpanded })
    protected val lineWidthSetting: Setting<Float> = add(NumberSetting("LineWidth", 1.0f, 0.1f, 3.0f).dependsOn(renderSetting))
    protected val fadeSetting: Setting<Boolean> = add(BooleanSetting("Fade", true).dependsOn(renderSetting))
    protected val fadeTimeSetting: Setting<Float> = add(NumberSetting("FadeTime", 1.0f, 0.1f, 1.0f).withUnit("s").dependsOn(renderSetting, fadeSetting))

    private val placementTimer = ElapsedTimer()
    private var pendingSwap: PendingSwap? = null
    private var useBanner = false

    init {
        val rendererSnapshot = ClientContext.featureBoxRenderer
        val renderConfiguration = FeatureBoxRenderer.RenderConfiguration(
            this, fillSetting, outlineSetting, lineWidthSetting, fadeTimeSetting,
            { true }, fadeSetting,
            450
        )
        rendererSnapshot.register(renderConfiguration)
    }

    protected fun selectPlacementPlanner(): AntiPhasePlacementPlanner {
        return when (materialSetting.value) {
            AntiPhaseMaterialChoice.LADDER -> AntiPhaseLadderPlanner()
            AntiPhaseMaterialChoice.SCAFFOLDING -> AntiPhaseFullBlockPlanner()
            AntiPhaseMaterialChoice.VINE -> AntiPhaseVinePlanner()
            AntiPhaseMaterialChoice.ITEMFRAME -> selectItemFramePlanner()
        }
    }

    protected fun shouldUseBanner(target: Player): Boolean {
        val targetPosition = PlayerStateUtils.getRoundedBlockPosition(target)
        if (bannerSetting.value) {
            if (WorldUtils.getBlock(targetPosition) !is AbstractBannerBlock) {
                return true
            }
        }
        return false
    }

    protected fun selectItemFramePlanner(): AntiPhasePlacementPlanner {
        return if (useBanner) AntiPhaseFullBlockPlanner() else AntiPhaseItemFramePlanner()
    }

    protected fun isValidTarget(target: Player): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val level = ClientAccess.collectiveMinecraft.level ?: return false

        if (target.isAlive && player !== target) {
            if (!target.isSwimming) {
                if (ClientContext.socialRegistry.isFriendPlayer(target)) {
                    return false
                }

                val occupiedPositions = PlayerStateUtils.getOccupiedBlockPositions(target)
                if (occupiedPositions.size != 1) {
                    return false
                }

                val roundedPosition = PlayerStateUtils.getRoundedBlockPosition(target)
                if (hasItemFrame(roundedPosition)) {
                    return false
                }

                if (!PlayerStateUtils.hasArmor(target)) {
                    return false
                }

                if (noCrawlSetting.value) {
                    val targetHeight = target.boundingBox.ysize
                    if (targetHeight <= 1.0) {
                        return false
                    }
                }

                if (smartSetting.value) {
                    val targetSpeed = MovementPhysics.getHorizontalSpeedKmh(target)
                    if (targetSpeed > 18.0) {
                        return false
                    }

                    val expandedBounds = target.boundingBox.inflate(0.08, 0.0, 0.08)
                    if (level.noBlockCollision(target, expandedBounds)) {
                        return false
                    }

                    if (!EntityUtils.hasCollisionBelow(target)) {
                        return false
                    }
                }

                val distance = target.distanceTo(player)
                return distance <= rangeSetting.value
            }
        }
        return false
    }

    private fun placeMaterial(placement: AntiPhasePlacement): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val materialItem = materialSetting.value.item

        var hotbarSlot = InventoryUtils.findHotbarSlot(materialItem)
        var inventorySlot = InventoryUtils.findMenuSlot(materialItem)

        if (useBanner) {
            val bannerPredicate = { stack: ItemStack -> stack.item is BannerItem }
            hotbarSlot = InventoryUtils.findHotbarSlot(bannerPredicate)
            inventorySlot = InventoryUtils.findInventorySlot(bannerPredicate, true)
        }

        val previousSlot = player.inventory.selected
        if (hotbarSlot == -1 && inventorySlot == -1) {
            return false
        }

        // Если прошлый откат ещё не выполнен (несколько установок за тик при BPT > 1),
        // сначала восстанавливаем слот — иначе pendingSwap перезаписывается и
        // исходный слот теряется навсегда.
        val existingSwap = pendingSwap
        if (existingSwap != null) {
            if (existingSwap.inventorySwap) {
                InventoryUtils.swapIntoSelectedSlot(existingSwap.slot)
            } else {
                InventoryUtils.selectHotbarSlotForPlacement(existingSwap.slot)
            }
            pendingSwap = null
        }

        if (hotbarSlot != -1) {
            InventoryUtils.selectHotbarSlot(hotbarSlot)
            pendingSwap = PendingSwap(previousSlot, false)
        } else {
            InventoryUtils.swapIntoSelectedSlot(inventorySlot)
            pendingSwap = PendingSwap(inventorySlot, true)
        }

        val hit = BlockHitResult(
            placement.hitPosition, placement.direction, placement.supportPosition, false
        )
        val interactionScheduler = ClientContext.playerStateTracker
        interactionScheduler.runSneaking {
            PacketSender.sendUseItemOn(InteractionHand.MAIN_HAND, hit)
            ClientContext.swingManager.swingPacket(InteractionHand.MAIN_HAND, SwingAction.INTERACT)
        }
        return true
    }

    protected fun hasItemFrame(position: BlockPos): Boolean {
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val boundsSnapshot = AABB(position)
        val checkedBounds = boundsSnapshot.expandTowards(0.0, 0.1, 0.0)

        for (entity in level.entitiesForRendering()) {
            if (entity is ItemFrame) {
                if (entity.boundingBox.intersects(checkedBounds)) {
                    return true
                }
            }
        }
        return false
    }

    @EventHandler
    private fun onPlayerUpdate(event: RotationRequestEvent) {
        val level = ClientAccess.collectiveMinecraft.level ?: return

        val delayMillis = delaySetting.value.toLong()

        if (placementTimer.hasElapsedAndReset(delayMillis)) {
            if (multiTask?.shouldPauseModule(this) == true) {
                return
            }

            val perTickLimit = blocksPerTickSetting.value

            var placedCount = 0
            for (target in level.players()) {
                useBanner = shouldUseBanner(target)

                if (isValidTarget(target)) {
                    val placement = selectPlacementPlanner().findPlacement(target)
                    if (placement != null) {
                        if (placeMaterial(placement)) {
                            if (rotateSetting.value) {
                                val rotations = RotationManager.getRotationsToPosition(placement.hitPosition)
                                ClientContext.rotationManager.requestRotation(rotations, 25)
                            }

                            if (renderSetting.value) {
                                ClientContext.featureBoxRenderer.addBox(this, placement.bounds)
                            }

                            placedCount++
                        }

                        if (placedCount >= perTickLimit) {
                            break
                        }
                    }
                }
            }

            val currentPendingSwap = pendingSwap
            if (currentPendingSwap != null) {
                if (currentPendingSwap.inventorySwap) {
                    InventoryUtils.swapIntoSelectedSlot(currentPendingSwap.slot)
                } else {
                    InventoryUtils.selectHotbarSlotForPlacement(currentPendingSwap.slot)
                }
                pendingSwap = null
            }
        }
    }

    private data class PendingSwap(
        val slot: Int,
        val inventorySwap: Boolean
    )

    companion object {
        private var multiTask: MultiTaskModule? = null

        init {
            multiTask = ClientContext.getModuleOrNull(MultiTaskModule::class.java)
        }

        @JvmStatic
        fun bindMultiTaskModule(registry: ModuleRegistry) {
            multiTask = registry.getModule(MultiTaskModule::class.java) as? MultiTaskModule
        }
    }
}
