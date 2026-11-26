package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import me.collective.client.events.PacketEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.managers.ServerStateManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.movement.NoFallModule
import me.collective.client.modules.movement.NoSlowModule
import me.collective.client.modules.player.NoInteractModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.settings.options.NoSlowStrictInventoryChoice
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.KeyUtils
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.inventory.ItemUtils
import me.collective.client.utils.world.BlockStateUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.AxeItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.ShieldItem
import net.minecraft.world.level.block.Blocks
import java.util.function.Predicate
import kotlin.math.abs

class OffhandModule : Module("Offhand", "Manages your offhand automatically.", ModuleCategory.COMBAT, "autototem"), LethalDamageTracker.LethalCheckPolicy {
    val itemSetting: Setting<OffhandItemChoice>
    val customSetting: Setting<Set<Item>>
    private val delaySetting: Setting<Int>
    val healthSetting: Setting<Float>
    val lethalSetting: Setting<Boolean>
    val elytraSetting: Setting<Boolean>
    private val directSwapSetting: Setting<Boolean>
    private val containersSetting: Setting<Boolean>
    val gappleBindSetting: Setting<Boolean>
    private val safeSetting: Setting<Float>
    private val safeInBlocksSetting: Setting<Float>
    private val axeSetting: Setting<Boolean>
    private val pickaxeSetting: Setting<Boolean>
    private val crystalsSetting: Setting<Boolean>
    private val totemsSetting: Setting<Boolean>
    private val crappleSetting: Setting<Boolean>
    private val swapDelayTimer: ElapsedTimer
    private val totemPopTimer: ElapsedTimer
    private var previousSwapSlot: Int
    private var previousMenuClass: Class<*>? = null
    private var tickAllowsSwap: Boolean = false
    private var deathReportSent: Boolean = false
    private var hasInventoryTotem: Boolean = false
    private val lethalDamageTracker: LethalDamageTracker

    init {
        this.itemSetting = this.add(EnumSetting("Item", OffhandItemChoice.Totem))
        this.customSetting = this.add(RegistrySetSetting("Custom", BuiltInRegistries.ITEM))
        this.delaySetting = this.add(NumberSetting("Delay", 25, 0, 500).withUnit("ms"))
        this.healthSetting = this.add(NumberSetting("Health", 16.0f, 0.0f, 36.0f).withUnit("hp"))
        this.lethalSetting = this.add(BooleanSetting("Lethal", true))
        this.elytraSetting = this.add(BooleanSetting("Elytra", true))
        this.directSwapSetting = this.add(BooleanSetting("1.17+", true))
        this.containersSetting = this.add(BooleanSetting("Containers", true))
        this.gappleBindSetting = this.add(BooleanSetting("GappleBind", true).markVisibilityDependency())

        val safeVisibility = Predicate<Any?> { this.gappleBindSetting.isExpanded }
        this.safeSetting = this.add(NumberSetting("Safe", 8.0f, 0.0f, 36.0f, safeVisibility).withUnit("hp"))
        this.safeInBlocksSetting = this.add(NumberSetting("SafeInBlocks", 0.0f, 0.0f, 36.0f).withUnit("hp").dependsOn(this.gappleBindSetting))
        this.axeSetting = this.add(BooleanSetting("Axe", true).dependsOn(this.gappleBindSetting))

        val pickaxeVisibility = Predicate<Any?> { this.gappleBindSetting.isExpanded }
        this.pickaxeSetting = this.add(BooleanSetting("Pickaxe", true, pickaxeVisibility))

        val crystalsVisibility = Predicate<Any?> { this.gappleBindSetting.isExpanded }
        this.crystalsSetting = this.add(BooleanSetting("Crystals", true, crystalsVisibility))
        this.totemsSetting = this.add(BooleanSetting("Totems", false).dependsOn(this.gappleBindSetting))

        val crappleVisibility = Predicate<Any?> {
            this.itemSetting.value == OffhandItemChoice.Gapple || this.gappleBindSetting.isExpanded
        }
        this.crappleSetting = this.add(BooleanSetting("Crapple", false, crappleVisibility))
        this.swapDelayTimer = ElapsedTimer()
        this.totemPopTimer = ElapsedTimer()
        this.previousSwapSlot = -1
        this.lethalDamageTracker = LethalDamageTracker(this)

        this.gappleBindSetting.setConfigName("GappleBind")
        this.safeSetting.setConfigName("SafeHealth")

        val customOption = this.customSetting
        customOption.setChangeCallback {
            val registryOption = customOption as RegistrySetSetting<Item>
            val lastChanged = registryOption.lastChangedEntry ?: return@setChangeCallback
            (customOption.value as MutableSet<Item>).clear()
            (customOption.value as MutableSet<Item>).add(lastChanged)
        }
        this.safeInBlocksSetting.setBoundaryLabel("None", SettingLabelCondition.MIN)
        this.customSetting.addVisibilityPredicate {
            this.itemSetting.value == OffhandItemChoice.Custom
        }
    }

    val isGappleBindSafeInBlocks: Boolean
        get() {
            if (!this.gappleBindSetting.value) return false

            val threshold = this.safeInBlocksSetting.value
            if (threshold >= EntityUtils.getPlayerHealthWithAbsorption()) return false

            val player = ClientAccess.collectiveMinecraft.player ?: return false
            val level = ClientAccess.collectiveMinecraft.level ?: return false
            val surroundingBlockPositions = PlayerStateUtils.getOccupiedBlockPositions(player)
            var qualifyingBlockCount = 0
            for (blockPos in surroundingBlockPositions) {
                val blockState = level.getBlockState(blockPos)
                if (BlockStateUtils.isBlastResistantBreakable(blockState)) qualifyingBlockCount++
                if (blockState.`is`(Blocks.BEDROCK)) return true
            }
            return qualifyingBlockCount > 2
        }

    override fun getInfo(): String {
        return this.itemSetting.value.toString()
    }

    override fun onToggle() {
        this.previousSwapSlot = -1
    }

    fun canUseGappleBind(item: Item): Boolean {
        val useCrapple = this.crappleSetting.value
        val gappleItem = OffhandItemChoice.Gapple.getItem(useCrapple)

        val player = ClientAccess.collectiveMinecraft.player ?: return false
        if (player.isUsingItem) {
            if (player.usedItemHand == InteractionHand.OFF_HAND) {
                if (gappleItem != null && player.useItem.`is`(gappleItem)) {
                    if (this.itemSetting.value != OffhandItemChoice.Crystal) {
                        if (!this.isMainhandManagingTotemOrFood()) return true
                    }
                }
            }
        }
        val defaultItemStack = item.defaultInstance
        if (ItemUtils.isSword(defaultItemStack) && item !is AxeItem) return true
        if (item is AxeItem) {
            if (this.axeSetting.value) return true
        }
        val defaultToolStack = item.defaultInstance
        if (defaultToolStack.`is`(ItemTags.PICKAXES)) {
            if (this.pickaxeSetting.value) return true
        }
        if (item == Items.TOTEM_OF_UNDYING) {
            if (this.totemsSetting.value) return true
        }
        if (item == Items.END_CRYSTAL) {
            if (this.crystalsSetting.value) return true
        }
        return false
    }

    fun applyGappleBind(incomingChoice: OffhandItemChoice?): OffhandItemChoice? {
        if (incomingChoice != null) {
            val useCrapple = this.crappleSetting.value
            if (incomingChoice.getItem(useCrapple) is ShieldItem) return incomingChoice
        }
        val hitResult = ClientAccess.collectiveMinecraft.hitResult
        val player = ClientAccess.collectiveMinecraft.player ?: return incomingChoice
        if (WorldUtils.isInteractive(hitResult)) {
            if (!player.isShiftKeyDown) {
                if (!noInteractModule.isToggled) return incomingChoice
            }
        }
        val currentHealthWithAbsorption = EntityUtils.getPlayerHealthWithAbsorption()
        val mainhandItem = player.mainHandItem.item

        var gappleHealthGate = this.safeSetting.value < currentHealthWithAbsorption
        val blockHealthThreshold = this.safeInBlocksSetting.value
        var blockSafetyApplied = false
        if (blockHealthThreshold < currentHealthWithAbsorption) {
            if (!this.safeInBlocksSetting.isAtMinimum) {
                var blockSafe = this.isGappleBindSafeInBlocks
                if (!blockSafe) {
                    if (player.isUsingItem) {
                        blockSafe = player.usedItemHand == InteractionHand.OFF_HAND
                    }
                }
                gappleHealthGate = blockSafe
                blockSafetyApplied = true
            }
        }
        if (!blockSafetyApplied && mainhandItem == Items.TOTEM_OF_UNDYING) gappleHealthGate = true
        if (!this.hasInventoryTotem) gappleHealthGate = true

        if (this.gappleBindSetting.value) {
            val useKey = ClientAccess.collectiveMinecraft.options.keyUse
            if (useKey.isDown && gappleHealthGate) {
                if (this.canUseGappleBind(mainhandItem) && ClientAccess.collectiveMinecraft.gui.screen() == null) {
                    if (this.itemSetting.value != OffhandItemChoice.Gapple) return OffhandItemChoice.Gapple
                }
            }
        }
        return incomingChoice
    }

    @EventHandler
    private fun onPlayerTickStart(event: LocalPlayerPreTickEvent) {
        updateTotemAvailability(this)
        if (!shouldUseStrictTick()) return
        this.tickAllowsSwap = true
        this.updateOffhand()
    }

    fun swapToChosenItem() {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val menuClass = player.containerMenu.javaClass
        val chosenItemChoice = this.selectOffhandChoice()
        val containerId = player.containerMenu.containerId

        val allowCrapple = this.crappleSetting.value
        var chosenContainerSlot = chosenItemChoice.findContainerSlot(allowCrapple)
        val canReusePreviousSlot = ClientAccess.collectiveMinecraft.gui.screen() !is AbstractContainerScreen<*>
            || ClientAccess.collectiveMinecraft.gui.screen() is InventoryScreen
        if (this.previousSwapSlot != -1 && chosenContainerSlot != -1 && canReusePreviousSlot
            && menuClass == this.previousMenuClass) {
            val previousSlotStack = player.containerMenu.getSlot(this.previousSwapSlot).item
            val chosenSlotStack = player.containerMenu.getSlot(chosenContainerSlot).item
            if (chosenSlotStack.`is`(previousSlotStack.item)) {
                chosenContainerSlot = this.previousSwapSlot
            }
        }
        if (chosenContainerSlot == -1) return

        if (!this.containersSetting.value && (ClientAccess.collectiveMinecraft.gui.screen() is ShulkerBoxScreen
            || ClientAccess.collectiveMinecraft.gui.screen() is ContainerScreen)) return
        if (chosenContainerSlot >= player.containerMenu.slots.size) return

        val carriedStackBeforeSwap = InventoryUtils.getCarriedStack()
        val targetSlotStack = player.containerMenu.getSlot(chosenContainerSlot).item
        if (targetSlotStack.has(DataComponents.FOOD) && !this.tickAllowsSwap) return

        this.beginInventorySwap()
        if (!carriedStackBeforeSwap.isEmpty) {
            if (!InventoryUtils.isInventoryFull()) {
                val emptyContainerSlot = InventoryUtils.findMenuSlot(Items.AIR)
                if (emptyContainerSlot != -1) {
                    InventoryUtils.clickSlot(emptyContainerSlot, 0, ContainerInput.PICKUP)
                }
            }
        }

        var useDirectSwap = false
        if (this.directSwapSetting.value) {
            useDirectSwap = InventoryUtils.getCarriedStack().isEmpty
        }
        var swapped = true
        if (useDirectSwap) {
            val swapGameMode = ClientAccess.collectiveMinecraft.gameMode
            swapped = swapGameMode != null
            swapGameMode?.handleContainerInput(containerId, chosenContainerSlot, 40, ContainerInput.SWAP, player)
            this.swapDelayTimer.reset()
        } else {
            swapped = this.swapSlotToOffhand(chosenContainerSlot)
        }
        if (swapped) {
            this.previousSwapSlot = chosenContainerSlot
            this.previousMenuClass = menuClass
            RECENT_SWAP_TIMER.reset()
        }
        this.endInventorySwap()

        if (swapped) {
            val useKey = ClientAccess.collectiveMinecraft.options.keyUse
            if (KeyUtils.isPhysicallyDown(useKey)) {
                if (targetSlotStack.has(DataComponents.FOOD)) {
                    if (player.isUsingItem && player.usedItemHand == InteractionHand.OFF_HAND) {
                        player.releaseUsingItem()
                    }
                    ClientAccess.collectiveMinecraft.gameMode?.useItem(player, InteractionHand.OFF_HAND)
                }
            }
        }
    }

    private fun canUseCurrentMenu(): Boolean {
        if (this.directSwapSetting.value) return true
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        return ClientAccess.collectiveMinecraft.gui.screen() !is CreativeModeInventoryScreen
            && player.inventoryMenu == player.containerMenu
            && ClientAccess.collectiveMinecraft.gui.screen() !is ContainerScreen
    }

    private fun swapSlotToOffhand(containerSlot: Int): Boolean {
        val delayMillis = this.delaySetting.value.toLong()
        if (!this.swapDelayTimer.hasElapsed(delayMillis)) return false
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        if (player.containerMenu.slots.size <= 45) return false
        InventoryUtils.swapSlotsWithPickup(containerSlot, 45)
        this.swapDelayTimer.reset()
        return true
    }

    private fun withGameLogTooltip(component: MutableComponent): MutableComponent {
        return component.withStyle { style -> style.withHoverEvent(HoverEvent.ShowText(Component.literal("More info in the game log."))) }
    }

    @EventHandler(priority = 100)
    private fun onClientTick(event: MinecraftRunTickEvent) {
        if (shouldUseStrictTick()) return
        this.tickAllowsSwap = event.hasPendingTicks
        this.updateOffhand()
    }

    @EventHandler
    private fun onUseItemOn(event: MultiPlayerGameModeUseItemOnEvent) {
        if (event.hand != InteractionHand.MAIN_HAND) return
        if (this.applyGappleBind(null) == null) return
        if (antiCheatModule.is2b2tEnabled) return

        val gameMode = ClientAccess.collectiveMinecraft.gameMode
        val player = ClientAccess.collectiveMinecraft.player
        if (player != null) {
            gameMode?.useItem(player, InteractionHand.OFF_HAND)
        }
        event.cancel()
    }

    @EventHandler
    private fun onPacketReceive(event: PacketEvent.Receive) {
        val packet = event.packet
        // Событие приходит из netty-потока: shrink/свапы инвентаря и чтение мира —
        // только с клиентского потока, иначе гонки с тик-циклом и рассинхрон инвентаря.
        ClientAccess.collectiveMinecraft.execute {
            val player = ClientAccess.collectiveMinecraft.player ?: return@execute
            val level = ClientAccess.collectiveMinecraft.level ?: return@execute

            if (packet is ClientboundEntityEventPacket) {
                if (!this.shouldDiscardTotem()) {
                    if (this.canUseCurrentMenu()) {
                        if (packet.getEntity(level) == player) {
                            if (packet.eventId.toInt() == 35) {
                                if (player.mainHandItem.`is`(Items.TOTEM_OF_UNDYING)) {
                                    player.mainHandItem.shrink(1)
                                } else if (player.offhandItem.`is`(Items.TOTEM_OF_UNDYING)) {
                                    player.offhandItem.shrink(1)
                                }

                                this.totemPopTimer.reset()
                                if (!shouldUseStrictTick()) {
                                    this.swapToChosenItem()
                                }
                            }
                        }
                    }
                }
            }

            if (packet is ClientboundPlayerCombatKillPacket) {
                if (player.offhandItem.`is`(Items.TOTEM_OF_UNDYING)) {
                    this.reportDeathDiagnostics()
                }
            }
        }
    }

    private fun shouldDiscardTotem(): Boolean {
        if (!autoCrystalModule.isToggled) return false
        if (!autoCrystalModule.forceSuicideSetting.value) return false
        return this.itemSetting.value == OffhandItemChoice.Totem
    }

    private fun reportDeathDiagnostics() {
        val moduleNameHash = this.name.hashCode()
        val messageId = -abs(moduleNameHash) - 1
        val messageSignature = ChatUtils.createSignature(messageId)

        val millisSinceTotemPop = this.totemPopTimer.elapsedMillis()
        val latencyMillis = ClientContext.serverStateManager.latencyMillis
        if (this.deathReportSent) return

        val diagnosticText = String.format("Since last pop: %dms. Latency: %dms.", millisSinceTotemPop, latencyMillis)
        val diagnosticComponent = Component.literal(diagnosticText)
        if (latencyMillis <= millisSinceTotemPop) {
            if (millisSinceTotemPop >= 25L) {
                if (this.isServerLagging()) {
                    if (millisSinceTotemPop < 200L) {
                        val serverLagComponent = Component.literal(" Possible death reasons: Server lag*")
                        diagnosticComponent.append(this.withGameLogTooltip(serverLagComponent))
                        ChatUtils.printChat(diagnosticComponent, messageSignature, ChatUtils.MessagePriority.HIGH)
                        ClientAccess.logger.warn("*Death was most likely caused by server lagging for too long.")
                        this.deathReportSent = true
                        return
                    }
                }
                return
            }
        }

        val damageOverrideComponent = Component.literal(" Possible death reasons: Damage override/Lag spike.*")
        diagnosticComponent.append(this.withGameLogTooltip(damageOverrideComponent))
        ChatUtils.printChat(diagnosticComponent, messageSignature, ChatUtils.MessagePriority.HIGH)
        ClientAccess.logger.warn("*Death was most likely caused by you taking damage before the server receives the update slot packet, making you die holding a totem client-side.")
        this.deathReportSent = true
    }

    fun endInventorySwap() {
        ClientContext.inventoryStateManager.setClickBatching(false)
        if (ClientAccess.collectiveMinecraft.gui.screen() !is AbstractContainerScreen<*>) {
            ClientContext.inventoryStateManager.close()
        }
    }

    fun beginInventorySwap() {
        ClientContext.inventoryStateManager.setClickBatching(true)
        noSlowModule.prepareForInventoryAction()
    }

    private fun isServerLagging(): Boolean {
        val latencyMillis = ClientContext.serverStateManager.latencyMillis
        val lastPacketTimestamp = ClientContext.serverStateManager.getLastPacketTime(ServerStateManager.PacketActivity.ANY)
        val timeSinceAnyPacket = System.currentTimeMillis() - lastPacketTimestamp
        if (timeSinceAnyPacket > latencyMillis * 2L) return true
        return ClientContext.serverStateManager.hasStablePacketFlowFor(500L)
    }

    fun isFallDistanceSafe(): Boolean {
        if (!this.lethalSetting.value) return true
        val player = ClientAccess.collectiveMinecraft.player ?: return true
        if (player.abilities.invulnerable) return true
        if (player.hasEffect(MobEffects.SLOW_FALLING)) return true
        if (noFallModule.isToggled) return true

        val trackedDownwardDistance = PlayerStateUtils.getTrackedFallDistance()
        return trackedDownwardDistance < 16.0f
    }

    override fun isLethalCheckEnabled(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        if (player.mainHandItem.`is`(Items.TOTEM_OF_UNDYING)) return false
        return this.lethalSetting.value
    }

    private fun updateOffhand() {
        if (this.isWorldUnavailable) return
        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (player.tickCount <= 1) return

        if (player.isAlive) this.deathReportSent = false

        this.lethalDamageTracker.update()
        if (this.shouldDiscardTotem()) {
            // Слот 45 существует только в inventoryMenu: при открытом сундуке THROW по
            // слоту 45 выбрасывал случайный слот открытого контейнера.
            if (player.offhandItem.`is`(Items.TOTEM_OF_UNDYING) && this.tickAllowsSwap
               && player.containerMenu === player.inventoryMenu) {
                val discardContainerId = player.containerMenu.containerId
                ClientAccess.collectiveMinecraft.gameMode?.handleContainerInput(discardContainerId, 45, 1, ContainerInput.THROW, player)
            }
            return
        }

        if (!this.canUseCurrentMenu()) return
        val delayMillis = this.delaySetting.value.toLong()
        if (!this.swapDelayTimer.hasElapsed(delayMillis)) return
        this.swapToChosenItem()
    }

    private fun selectOffhandChoice(): OffhandItemChoice {
        if (autoCrystalModule.isToggled) {
            if (autoCrystalModule.forceSuicideSetting.value) {
                if (this.itemSetting.value != OffhandItemChoice.Totem) return OffhandItemChoice.Crystal
            }
        }

        val useCrapple = this.crappleSetting.value
        var selectedChoice = OffhandItemChoice.Totem

        if (this.lethalDamageTracker.shouldHoldTotem() && this.hasInventoryTotem) return OffhandItemChoice.Totem
        val currentHealthWithAbsorption = EntityUtils.getPlayerHealthWithAbsorption()
        val healthThreshold = this.healthSetting.value
        if (currentHealthWithAbsorption > healthThreshold) {
            if (this.isFallDistanceSafe()) {
                val configuredItem = this.itemSetting.value.getItem(useCrapple)
                if (configuredItem != null && InventoryUtils.countItems { it.`is`(configuredItem) } > 0) {
                    selectedChoice = this.itemSetting.value
                }
            }
        }

        if (this.elytraSetting.value) {
            if (PlayerStateUtils.isLocalWearingElytra()) selectedChoice = OffhandItemChoice.Totem
        }
        if (!this.hasInventoryTotem) {
            selectedChoice = this.itemSetting.value
        }
        return this.applyGappleBind(selectedChoice) ?: selectedChoice
    }

    fun isMainhandManagingTotemOrFood(): Boolean {
        if (!mainhandModule.isToggled) return false
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val selectedHotbarSlot = player.inventory.selectedSlot
        if (selectedHotbarSlot != mainhandModule.configuredHotbarSlot) return false

        val mainhandStack = player.mainHandItem
        if (mainhandStack.`is`(Items.TOTEM_OF_UNDYING)) return true
        return mainhandStack.has(DataComponents.FOOD)
    }

    companion object {
        private val RECENT_SWAP_TIMER = ElapsedTimer()

        @JvmStatic
        lateinit var autoCrystalModule: AutoCrystalModule

        @JvmStatic
        lateinit var offhandModule: OffhandModule

        @JvmStatic
        lateinit var noInteractModule: NoInteractModule

        @JvmStatic
        lateinit var antiCheatModule: AntiCheatModule

        @JvmStatic
        lateinit var noSlowModule: NoSlowModule

        @JvmStatic
        lateinit var mainhandModule: MainhandModule

        @JvmStatic
        lateinit var noFallModule: NoFallModule

        @JvmStatic
        fun isRecentSwapWindowActive(): Boolean {
            if (RECENT_SWAP_TIMER.hasElapsed(50L)) return false
            return offhandModule.isToggled
        }

        private fun shouldUseStrictTick(): Boolean {
            if (RotationManager.isSilentRotationMode()) return false
            return noSlowModule.strictInventoryMode == NoSlowStrictInventoryChoice.GRIM_STRICT
        }

        private fun updateTotemAvailability(owner: OffhandModule) {
            if (owner.isWorldUnavailable) return
            val player = ClientAccess.collectiveMinecraft.player ?: return
            var totemFound = false
            for (slot in player.inventoryMenu.slots) {
                if (slot.item.`is`(Items.TOTEM_OF_UNDYING)) {
                    totemFound = true
                    break
                }
            }
            owner.hasInventoryTotem = totemFound
        }

        @JvmStatic
        fun bindModuleConnections(modules: ModuleRegistry) {
            autoCrystalModule = modules.getModule(AutoCrystalModule::class.java) as AutoCrystalModule
            offhandModule = modules.getModule(OffhandModule::class.java) as OffhandModule
            noInteractModule = modules.getModule(NoInteractModule::class.java) as NoInteractModule
            antiCheatModule = modules.getModule(AntiCheatModule::class.java) as AntiCheatModule
            noSlowModule = modules.getModule(NoSlowModule::class.java) as NoSlowModule
            mainhandModule = modules.getModule(MainhandModule::class.java) as MainhandModule
            noFallModule = modules.getModule(NoFallModule::class.java) as NoFallModule
            OffhandItemChoice.bindModuleRegistry(modules)
        }
    }
}
