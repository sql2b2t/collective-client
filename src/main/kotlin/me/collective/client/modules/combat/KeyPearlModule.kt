package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.EventStage
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.movement.ElytraFlyModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.combat.RaycastUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.inventory.InventoryUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult

class KeyPearlModule : Module("KeyPearl", "Throws an ender pearl.", ModuleCategory.COMBAT) {
    private val noEntityTraceSetting: Setting<Boolean> = this.add(BooleanSetting("NoEntityTrace", true))
    private val solidSetting: Setting<Boolean> = this.add(BooleanSetting("Solid", true))
    private val disableOnElytraSetting: Setting<Boolean> = this.add(BooleanSetting("DisableOnElytra", false))
    private val disableOnRidingSetting: Setting<Boolean> = this.add(BooleanSetting("DisableOnRiding", false))
    private val awaitSetting: Setting<Boolean> = this.add(BooleanSetting("Await", false))

    init {
        this.isDrawn = false
    }

    override fun onEnable() {
        if (this.isWorldUnavailable) {
            this.disable()
            return
        }

        if (!this.awaitSetting.value) {
            if (PlayerStateUtils.isEnderPearlOnCooldown()) {
                this.disable()
                return
            }
        }

        var shouldDisable = false
        val player = ClientAccess.collectiveMinecraft.player

        if (elytrafly.isToggled) {
            if (this.disableOnElytraSetting.value) {
                if (player != null && !player.onGround()) {
                    shouldDisable = PlayerStateUtils.isWearingElytra(player)
                }
            }
        }

        if (player != null && player.isPassenger) {
            if (this.disableOnRidingSetting.value) {
                shouldDisable = true
            }
        }

        if (this.disableOnElytraSetting.value) {
            val timerSnapshot = ClientContext.playerStateTracker.fallFlyingTimer
            if (!timerSnapshot.hasElapsed(150L)) {
                shouldDisable = true
            }
        }

        if (shouldDisable) {
            this.disable()
        }
    }

    private fun isObstructingBlock(position: BlockPos): Boolean {
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val state = level.getBlockState(position)
        if (!state.canBeReplaced()) {
            if (!state.`is`(Blocks.COBWEB)) {
                return true
            }
        }
        return false
    }

    private fun swapPearlSlot(slot: Int, inventorySwap: Boolean) {
        if (inventorySwap) {
            InventoryUtils.swapIntoSelectedSlot(slot)
        } else {
            InventoryUtils.selectHotbarSlotForPlacement(slot)
        }
    }

    @EventHandler(priority = 100)
    private fun onSendPosition(event: LocalPlayerSendPositionEvent) {
        if (event.stage != EventStage.Pre) {
            return
        }

        if (this.awaitSetting.value) {
            if (PlayerStateUtils.isEnderPearlOnCooldown()) {
                return
            }
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return
        val hitResult = RaycastUtils.raycastWithEntityTrace(player)
        val hotbarPearlSlot = InventoryUtils.findHotbarSlot(Items.ENDER_PEARL)
        val inventoryPearlSlot = InventoryUtils.findMenuSlot(Items.ENDER_PEARL)
        val previousSlot = player.inventory.selectedSlot
        var entityTraceBlocked = false

        if (this.noEntityTraceSetting.value) {
            if (hitResult is EntityHitResult) {
                val hitEntity = hitResult.entity
                if (!hitEntity.boundingBox.contains(player.eyePosition)) {
                    entityTraceBlocked = true
                }
            }
        }

        if (inventoryPearlSlot != -1 && !entityTraceBlocked) {
            if (hitResult is BlockHitResult) {
                val blockPositionSnapshot = hitResult.blockPos
                if (this.isObstructingBlock(blockPositionSnapshot)) {
                    if (hitResult.type != HitResult.Type.MISS) {
                        if (!this.solidSetting.value) {
                            this.disable()
                            return
                        }
                    }
                }
            }

            if (!OffhandModule.isRecentSwapWindowActive()) {
                val useInventorySwap = hotbarPearlSlot == -1
                val selectedPearlSlot = if (useInventorySwap) inventoryPearlSlot else hotbarPearlSlot
                this.swapPearlSlot(selectedPearlSlot, useInventorySwap)

                val gameMode = ClientAccess.collectiveMinecraft.gameMode
                gameMode?.useItem(player, InteractionHand.MAIN_HAND)

                val swingManager = ClientContext.swingManager
                swingManager.swing(InteractionHand.MAIN_HAND, SwingAction.INTERACT)

                val previousPearlSlot = if (useInventorySwap) inventoryPearlSlot else previousSlot
                this.swapPearlSlot(previousPearlSlot, useInventorySwap)

                ClientContext.moduleRegistry.placementModules
                    .filter { placementModule -> placementModule.placementPearlSetting.value }
                    .forEach { it.disable() }

                this.disable()
            }
        } else {
            this.disable()
        }
    }

    companion object {
        lateinit var elytrafly: ElytraFlyModule

        @JvmStatic
        fun bindReferencedModules(registry: ModuleRegistry) {
            elytrafly = registry.getModule(ElytraFlyModule::class.java) as ElytraFlyModule
        }
    }
}
