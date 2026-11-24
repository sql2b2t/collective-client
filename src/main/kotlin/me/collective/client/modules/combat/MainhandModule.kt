package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.player.AutoEatModule
import me.collective.client.modules.player.NoInteractModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.KeyUtils
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.inventory.ItemUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.component.DataComponents
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.HitResult

class MainhandModule : Module("Mainhand", "Auto totem for main hand.", ModuleCategory.COMBAT), LethalDamageTracker.LethalCheckPolicy {
    internal val slotSetting: Setting<Int> = add(NumberSetting("Slot", 1, 1, 9))
    internal val delaySetting: Setting<Int> = add(NumberSetting("Delay", 25, 0, 500).withUnit("ms"))
    private val safeSetting: Setting<Float> = add(NumberSetting("Safe", 8.0f, 0.0f, 36.0f).withUnit("hp"))
    private val autoSelectSetting: Setting<Boolean> = add(BooleanSetting("AutoSelect", false).markVisibilityDependency())
    private val autoSelectDelaySetting: Setting<Int> = add(NumberSetting("Delay", 25, 0, 500).withUnit("ms").dependsOn(autoSelectSetting))
    private val lethalSetting: Setting<Boolean> = add(BooleanSetting("Lethal", true).dependsOn(autoSelectSetting))
    private val healthSetting: Setting<Float> = add(NumberSetting("Health", 8.0f, 0.0f, 36.0f).withUnit("hp").dependsOn(autoSelectSetting))
    private val gappleBindSetting: Setting<Boolean> = add(BooleanSetting("GappleBind", true).markVisibilityDependency())
    private val fallbackSetting: Setting<Boolean> = add(BooleanSetting("Fallback", true).dependsOn(gappleBindSetting))
    private val crappleSetting: Setting<Boolean> = add(BooleanSetting("Crapple", false).dependsOn(gappleBindSetting))
    private val hotbarSetting: Setting<Boolean> = add(BooleanSetting("Hotbar", false).markVisibilityDependency())
    private val weaponSetting: Setting<Boolean> = add(BooleanSetting("Weapon", false).dependsOn(hotbarSetting))
    private val totemSetting: Setting<Boolean> = add(BooleanSetting("Totem", false).dependsOn(hotbarSetting))

    private val replacementTimer = ElapsedTimer()
    private val autoSelectTimer = ElapsedTimer()
    private val interactionCooldownTimer = ElapsedTimer()
    private var tickReady = false
    private var previousReplacementSlot = -1
    private var temporaryHotbarSelection = false
    private var previousSelectedHotbarSlot = -1
    private val lethalDamageTracker = LethalDamageTracker(this)

    init {
        autoSelectDelaySetting.setConfigName("SwapDelay")
    }

    override fun getInfo(): String {
        val player = ClientAccess.collectiveMinecraft.player ?: return "0"
        val desired = getDesiredItem()
        val itemCount = player.inventory.nonEquipmentItems.stream()
            .filter { stack -> stack.item == desired }
            .mapToInt { it.count }
            .sum()
        return itemCount.toString()
    }

    override fun onEnable() {
        previousReplacementSlot = -1
        previousSelectedHotbarSlot = -1
        temporaryHotbarSelection = false
    }

    override fun onDisable() {
        if (isWorldUnavailable) {
            temporaryHotbarSelection = false
        } else {
            restoreTemporaryHotbarSelection()
        }
        previousSelectedHotbarSlot = -1
    }

    private fun restoreTemporaryHotbarSelection() {
        if (temporaryHotbarSelection) {
            if (previousSelectedHotbarSlot != -1) {
                InventoryUtils.selectHotbarSlotForPlacement(previousSelectedHotbarSlot)
            }
            temporaryHotbarSelection = false
        }
    }

    private fun shouldTemporarilySelectGapple(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val mainhandStack = player.mainHandItem

        val offhand = offhandModule
        if (offhand != null && offhand.isToggled) {
            if (offhand.isGappleBindSafeInBlocks) {
                if (offhand.canUseGappleBind(mainhandStack.item)) {
                    return false
                }
            }
        }
        if (!canUseGappleBind()) {
            return false
        }
        if (mainhandStack.`is`(ItemTags.SWORDS) || mainhandStack.`is`(Items.MACE)) {
            if (weaponSetting.value) {
                return true
            }
        }
        if (mainhandStack.`is`(Items.TOTEM_OF_UNDYING)) {
            if (totemSetting.value) {
                return true
            }
        }
        return temporaryHotbarSelection
    }

    fun getConfiguredHotbarSlot(): Int {
        return slotSetting.value - 1
    }

    val configuredHotbarSlot: Int
        @JvmName("getConfiguredHotbarSlotProp") get() = getConfiguredHotbarSlot()

    private fun canUseGappleBind(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val hitResult = ClientAccess.collectiveMinecraft.hitResult
        if (WorldUtils.isInteractive(hitResult)) {
            if (!player.isShiftKeyDown) {
                if (noInteractModule?.isToggled != true) {
                    if (!player.isUsingItem) return false
                    if (player.usedItemHand != InteractionHand.MAIN_HAND) return false
                    if (!ItemUtils.isFood(player.mainHandItem)) return false
                }
            }
        }
        val useKey = ClientAccess.collectiveMinecraft.options.keyUse
        if (!useKey.isDown) return false
        val health = EntityUtils.getPlayerHealthWithAbsorption()
        return health >= safeSetting.value
    }

    private fun autoSelectTotem() {
        if (autoSelectSetting.value) {
            val player = ClientAccess.collectiveMinecraft.player ?: return
            val configuredStack = player.inventory.getItem(getConfiguredHotbarSlot())
            if (configuredStack.item == Items.TOTEM_OF_UNDYING) {
                if (player.isUsingItem && player.usedItemHand == InteractionHand.MAIN_HAND) {
                    return
                }

                if (!lethalDamageTracker.shouldHoldTotem()) {
                    val health = EntityUtils.getPlayerHealthWithAbsorption()
                    if (health > healthSetting.value) {
                        return
                    }
                }

                if (autoSelectTimer.hasElapsedAndReset(autoSelectDelaySetting.value.toLong())) {
                    InventoryUtils.selectHotbarSlotForPlacement(getConfiguredHotbarSlot())
                }
            }
        }
    }

    private fun getGappleItem(): Item {
        return if (crappleSetting.value) Items.GOLDEN_APPLE else Items.ENCHANTED_GOLDEN_APPLE
    }

    private fun getDesiredItem(): Item {
        val player = ClientAccess.collectiveMinecraft.player
        val selectedSlot = player?.inventory?.selectedSlot ?: -1
        if (selectedSlot == getConfiguredHotbarSlot()) {
            if (gappleBindSetting.value && canStartGappleBind() && canUseGappleBind()) {
                return getGappleItem()
            }
        }

        if (gappleBindSetting.value && fallbackSetting.value && !hasTotemAvailable()) {
            return getGappleItem()
        }
        return Items.TOTEM_OF_UNDYING
    }

    private fun hasTotemAvailable(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val configuredStack = player.inventory.getItem(getConfiguredHotbarSlot())
        if (configuredStack.`is`(Items.TOTEM_OF_UNDYING)) {
            return true
        }
        val totemSlot = InventoryUtils.findInventorySlot({ stack -> stack.`is`(Items.TOTEM_OF_UNDYING) }, true)
        return totemSlot != -1
    }

    private fun selectGappleTemporarily() {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (!player.isUsingItem && !player.mainHandItem.`is`(getGappleItem())) {
            val gappleSlot = InventoryUtils.findHotbarSlot(getGappleItem())
            if (gappleSlot == -1) {
                return
            }
            previousSelectedHotbarSlot = InventoryUtils.getSelectedSlot()
            InventoryUtils.selectHotbarSlotForPlacement(gappleSlot)
            temporaryHotbarSelection = true
        }
    }

    private fun canStartGappleBind(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val mainhandStack = player.mainHandItem

        if (ItemUtils.isFood(player.offhandItem)) {
            return false
        }
        if (!mainhandStack.`is`(Items.TOTEM_OF_UNDYING) && !mainhandStack.`is`(getGappleItem())) {
            return false
        }
        return true
    }

    @EventHandler(priority = 100)
    private fun onMinecraftRunTickEvent(event: MinecraftRunTickEvent) {
        if (isWorldUnavailable) {
            return
        }
        tickReady = event.hasPendingTicks

        lethalDamageTracker.update()
        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (player.containerMenu !== player.inventoryMenu) {
            return
        }

        val autoCrystal = autoCrystalModule
        if (autoCrystal != null && autoCrystal.isToggled && autoCrystal.forceSuicideSetting.value) {
            return
        }

        val autoEat = autoEatModule
        if (autoEat != null && autoEat.isToggled && autoEat.isEating()) {
            return
        }

        val autoExp = autoExpModule
        if (autoExp != null && autoExp.isToggled && autoExp.isActive()) {
            return
        }

        if (hotbarSetting.value && tickReady) {
            if (shouldTemporarilySelectGapple()) {
                selectGappleTemporarily()
                return
            }
            if (temporaryHotbarSelection) {
                restoreTemporaryHotbarSelection()
                return
            }
        }
        if (tickReady) {
            autoSelectTotem()
        }
        val desiredItem = getDesiredItem()

        val configuredStack = player.inventory.getItem(getConfiguredHotbarSlot())
        if (desiredItem != configuredStack.item) {
            replaceConfiguredSlot(desiredItem)
        }
    }

    @EventHandler
    private fun onMultiPlayerGameModeUseItemOnEvent(event: MultiPlayerGameModeUseItemOnEvent) {
        val hitResult = event.hitResult
        if (!WorldUtils.isInteractive(hitResult)) {
            return
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (player.isShiftKeyDown) {
            return
        }

        if (noInteractModule?.isToggled != true && !event.isCancelled) {
            interactionCooldownTimer.reset()
        }
    }

    private fun replaceConfiguredSlot(desiredItem: Item) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        var replacementSlot = InventoryUtils.findInventorySlot({ stack -> stack.`is`(desiredItem) }, true)
        if (replacementSlot < 9) {
            replacementSlot = InventoryUtils.toMenuSlot(replacementSlot)
        }
        if (previousReplacementSlot != -1) {
            val previousStack = player.inventory.getItem(previousReplacementSlot)
            if (previousStack.`is`(desiredItem) && replacementSlot != -1) {
                replacementSlot = previousReplacementSlot
            }
        }
        if (replacementSlot != -1) {
            val menuSlots = player.containerMenu.slots
            if (replacementSlot < menuSlots.size) {
                val menu = player.containerMenu
                val slot = menu.getSlot(replacementSlot)
                val replacementStack = slot.item
                if (!replacementStack.has(DataComponents.FOOD) || tickReady) {
                    if (replacementTimer.hasElapsedAndReset(delaySetting.value.toLong())) {
                        if (interactionCooldownTimer.hasElapsed(200L)) {
                            val configuredSlot = getConfiguredHotbarSlot()
                            InventoryUtils.swapWithHotbarSlot(replacementSlot, configuredSlot)
                            ClientContext.inventoryStateManager.close()
                            previousReplacementSlot = replacementSlot
                        }
                    }
                    val useKey = ClientAccess.collectiveMinecraft.options.keyUse
                    if (KeyUtils.isPhysicallyDown(useKey) && replacementStack.has(DataComponents.FOOD)) {
                        if (player.isUsingItem && player.usedItemHand == InteractionHand.OFF_HAND) {
                            player.releaseUsingItem()
                        }

                        val gameMode = ClientAccess.collectiveMinecraft.gameMode
                        gameMode?.useItem(player, InteractionHand.OFF_HAND)
                    }
                }
            }
        }
    }

    override fun isLethalCheckEnabled(): Boolean {
        if (!lethalSetting.value) {
            return false
        }
        return autoSelectSetting.value
    }

    companion object {
        private var noInteractModule: NoInteractModule? = null
        private var autoCrystalModule: AutoCrystalModule? = null
        private var autoEatModule: AutoEatModule? = null
        private var autoExpModule: AutoExpModule? = null
        private var offhandModule: OffhandModule? = null

        init {
            noInteractModule = ClientContext.getModule(NoInteractModule::class.java) as? NoInteractModule
            autoCrystalModule = ClientContext.getModule(AutoCrystalModule::class.java) as? AutoCrystalModule
            autoEatModule = ClientContext.getModule(AutoEatModule::class.java) as? AutoEatModule
            autoExpModule = ClientContext.getModule(AutoExpModule::class.java) as? AutoExpModule
            offhandModule = ClientContext.getModule(OffhandModule::class.java) as? OffhandModule
        }

        @JvmStatic
        fun bindModuleConnections(registry: ModuleRegistry) {
            noInteractModule = registry.getModule(NoInteractModule::class.java) as? NoInteractModule
            autoCrystalModule = registry.getModule(AutoCrystalModule::class.java) as? AutoCrystalModule
            autoEatModule = registry.getModule(AutoEatModule::class.java) as? AutoEatModule
            autoExpModule = registry.getModule(AutoExpModule::class.java) as? AutoExpModule
            offhandModule = registry.getModule(OffhandModule::class.java) as? OffhandModule
        }
    }
}
