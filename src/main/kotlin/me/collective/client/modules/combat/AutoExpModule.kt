package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.RotationRequestEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.misc.AntiAimModule
import me.collective.client.modules.movement.ElytraFlyModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.combat.DamageUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.inventory.EnchantmentUtils
import me.collective.client.utils.inventory.EquipmentUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.network.PacketSender
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

class AutoExpModule : Module("AutoExp", "Mends your armor for you.", ModuleCategory.COMBAT) {
    @JvmField
    val modeSetting: Setting<ModeChoice> = add(EnumSetting("Mode", ModeChoice.SILENT))
    private val delaySetting: Setting<Int> = add(NumberSetting("Delay", 2, 0, 5))
    private val frequencySetting: Setting<Int> = add(NumberSetting("Frequency", 1, 1, 16))
    private val rotateSetting: Setting<Boolean> = add(BooleanSetting("Rotate", false))
    private val airSetting: Setting<Boolean> = add(BooleanSetting("Air", true))
    private val inventorySetting: Setting<Boolean> = add(BooleanSetting("Inventory", false))
    internal val stopSetting: Setting<Boolean> = add(BooleanSetting("Stop", true).markVisibilityDependency())
    private val stopAtSetting: Setting<Int> = add(NumberSetting("StopAt", 100, 80, 100) { stopSetting.isExpanded }.withUnit("%"))
    private val autoDisableSetting: Setting<Boolean> = add(BooleanSetting("AutoDisable", false).dependsOn(stopSetting))
    private val toolsSetting: Setting<Boolean> = add(BooleanSetting("Tools", false).dependsOn(stopSetting))

    private val throwTimer = ElapsedTimer()
    private val inventorySwaps = ArrayList<Pair<Int, Int>>()
    private var originalSelectedSlot = -1
    private var active = false

    @JvmField
    var stopped: Boolean = false

    fun isActive(): Boolean = active

    override fun getInfo(): String {
        return if (stopped) {
            "Stopped"
        } else {
            val bottleCount = InventoryUtils.countItems { stack -> stack.`is`(Items.EXPERIENCE_BOTTLE) }
            bottleCount.toString()
        }
    }

    override fun onEnable() {
        if (!isWorldUnavailable) {
            if (rotateSetting.value) {
                throwTimer.reset()
            }

            if (antiAim?.isToggled == true && !stopped) {
                antiAim?.pauseRotations()
            }

            val player = ClientAccess.collectiveMinecraft.player
            if (player != null) {
                originalSelectedSlot = player.inventory.selectedSlot
            }
        }
    }

    override fun onDisable() {
        active = false
        if (isWorldUnavailable) {
            inventorySwaps.clear()
        } else {
            restoreInventorySwap(this)

            val player = ClientAccess.collectiveMinecraft.player
            if (player != null && player.inventory.selectedSlot != originalSelectedSlot) {
                if (modeSetting.value == ModeChoice.NORMAL) {
                    InventoryUtils.selectHotbarSlotForPlacement(originalSelectedSlot)
                }
            }
        }
    }

    @EventHandler(priority = 250)
    private fun updateMending(event: RotationRequestEvent) {
        active = false
        performMending(this)
    }

    private fun needsMending(stack: ItemStack): Boolean {
        if (!EnchantmentUtils.hasEnchantment(Enchantments.MENDING, stack)) {
            return false
        }
        if (!stack.isDamageableItem || stack.isEmpty) {
            return false
        }

        val durabilityPercent = DamageUtils.getDurabilityPercent(stack)
        return durabilityPercent < stopAtSetting.value
    }

    private fun swapBottlesFromInventory(): Boolean {
        if (!inventorySetting.value) {
            return false
        }

        val bottleInventorySlot = InventoryUtils.findMenuSlot(Items.EXPERIENCE_BOTTLE)
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val selectedHotbarSlot = player.inventory.selectedSlot
        if (bottleInventorySlot == -1) {
            return false
        }

        InventoryUtils.clickSlot(bottleInventorySlot, selectedHotbarSlot, ClickType.SWAP)
        inventorySwaps.add(Pair(bottleInventorySlot, selectedHotbarSlot))
        return true
    }

    private fun shouldPauseMending(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return true
        val level = ClientAccess.collectiveMinecraft.level ?: return true

        if (!airSetting.value) {
            val hitResult = ClientAccess.collectiveMinecraft.hitResult
            if (hitResult == null || hitResult.type == HitResult.Type.MISS) {
                return true
            }

            if (hitResult is BlockHitResult) {
                val state = level.getBlockState(hitResult.blockPos)
                if (state.canBeReplaced()) {
                    return true
                }
            }
        }

        return if (!OffhandModule.isRecentSwapWindowActive()) {
            player.isFallFlying && !canMendDuringFlight()
        } else {
            true
        }
    }

    private fun canMendDuringFlight(): Boolean {
        if (!rotateSetting.value) {
            return false
        }
        val elytra = elytraFly ?: return false
        val mode = elytra.modeSetting.value
        return elytra.isToggled &&
            PlayerStateUtils.isFallFlying() && !MovementPhysics.isLocalPlayerMoving() &&
            (mode == ElytraFlyModule.ModeChoice.CONTROL || mode == ElytraFlyModule.ModeChoice.NEWGRIM)
    }

    private fun calculateThrowRotations(): FloatArray {
        val player = ClientAccess.collectiveMinecraft.player ?: return floatArrayOf(0f, 0f)
        if (canMendDuringFlight()) {
            return floatArrayOf(player.yRot, -90.0f)
        }
        val playerBounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(player, 1)
        return RotationManager.getRotationsToPosition(BoundingBoxUtils.getBottomCenter(playerBounds))
    }

    enum class ModeChoice(private val displayName: String) : NamedEntry {
        NORMAL("Normal"),
        SILENT("Silent");

        override fun getName(): String = displayName
    }

    companion object {
        private var antiAim: AntiAimModule? = null
        private var elytraFly: ElytraFlyModule? = null

        init {
            antiAim = ClientContext.getModule(AntiAimModule::class.java) as? AntiAimModule
            elytraFly = ClientContext.getModule(ElytraFlyModule::class.java) as? ElytraFlyModule
        }

        private fun performMending(owner: AutoExpModule) {
            val player = ClientAccess.collectiveMinecraft.player ?: return
            if (owner.shouldPauseMending()) {
                return
            }

            val targetRotation = owner.calculateThrowRotations()
            val bottleSlot = InventoryUtils.findSwapSlot(Items.EXPERIENCE_BOTTLE, false)
            var holdingBottles = player.isHolding(Items.EXPERIENCE_BOTTLE)
            if (!holdingBottles && !bottleSlot.isFound) {
                if (owner.swapBottlesFromInventory()) {
                    holdingBottles = true
                }
            }

            if (owner.stopSetting.value) {
                var allItemsRepaired = true
                for (armorStack in EquipmentUtils.getArmorStacks(player)) {
                    if (owner.needsMending(armorStack)) {
                        allItemsRepaired = false
                        break
                    }
                }

                if (owner.toolsSetting.value && (owner.needsMending(player.mainHandItem) || owner.needsMending(player.offhandItem))) {
                    allItemsRepaired = false
                }

                if (allItemsRepaired || (!bottleSlot.isFound && !holdingBottles)) {
                    owner.stopped = true
                    if (owner.autoDisableSetting.value) {
                        owner.disable()
                    }
                    return
                }
            }

            if (bottleSlot.isFound || holdingBottles) {
                val delayTicks = owner.delaySetting.value
                val delayMillis = delayTicks.toLong() * 50
                if (owner.throwTimer.hasElapsed(delayMillis)) {
                    owner.stopped = false
                    val previousSlot = player.inventory.selectedSlot
                    InventoryUtils.swapTo(bottleSlot)

                    for (bottlesThrown in 0 until owner.frequencySetting.value) {
                        if (owner.rotateSetting.value) {
                            PacketSender.sendUseItem(bottleSlot.hand, targetRotation[0], targetRotation[1])
                        } else {
                            PacketSender.sendUseItem(bottleSlot.hand)
                        }
                    }

                    ClientContext.swingManager.swingPacket(bottleSlot.hand, SwingAction.INTERACT)

                    if (owner.modeSetting.value == ModeChoice.SILENT) {
                        InventoryUtils.swapBack(previousSlot, bottleSlot)
                        restoreLastInventorySwap(owner)
                    }

                    owner.throwTimer.reset()
                }
            }

            if (owner.rotateSetting.value) {
                ClientContext.rotationManager.requestRotation(targetRotation, 400)
            }

            owner.active = true
        }

        private fun restoreInventorySwap(owner: AutoExpModule) {
            // Откатываем все свапы из инвентаря в обратном порядке выполнения.
            for (index in owner.inventorySwaps.indices.reversed()) {
                val (inventorySlot, hotbarSlot) = owner.inventorySwaps[index]
                InventoryUtils.clickSlot(inventorySlot, hotbarSlot, ClickType.SWAP)
            }
            owner.inventorySwaps.clear()
        }

        private fun restoreLastInventorySwap(owner: AutoExpModule) {
            if (owner.inventorySwaps.isEmpty()) {
                return
            }
            val (inventorySlot, hotbarSlot) = owner.inventorySwaps.removeAt(owner.inventorySwaps.size - 1)
            InventoryUtils.clickSlot(inventorySlot, hotbarSlot, ClickType.SWAP)
        }

        @JvmStatic
        fun bindReferencedModules(modules: ModuleRegistry) {
            antiAim = modules.getModule(AntiAimModule::class.java) as? AntiAimModule
            elytraFly = modules.getModule(ElytraFlyModule::class.java) as? ElytraFlyModule
        }
    }
}
