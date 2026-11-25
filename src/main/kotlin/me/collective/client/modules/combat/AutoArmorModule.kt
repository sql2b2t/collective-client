package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.MinecraftTickEvent
import me.collective.client.events.ScreenChangeEvent
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.managers.InventoryStateManager
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.movement.ElytraFlyModule
import me.collective.client.modules.movement.NoSlowModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.settings.options.AutoArmorBlastChoice
import me.collective.client.settings.options.AutoArmorHelmetChoice
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.combat.DamageUtils
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.ArmorSlot
import me.collective.client.utils.inventory.EnchantmentUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.movement.NewGrimElytraFlyHandler
import me.collective.client.utils.movement.WallsVelocityHandler
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.WorldUtils
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.tags.EnchantmentTags
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.piglin.Piglin
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments

class AutoArmorModule : Module("AutoArmor", "Equips the best armor in your inventory for you.", ModuleCategory.COMBAT) {
    private val frequencySetting: Setting<Int> = add(NumberSetting("Frequency", 1, 1, 4))
    private val delaySetting: Setting<Int> = add(NumberSetting("Delay", 50, 0, 500).withUnit("ms"))

    @JvmField
    val alternativeSetting: Setting<Boolean> = add(BooleanSetting("Alternative", false))

    internal val safeSetting: Setting<Boolean> = add(BooleanSetting("Safe", false).markVisibilityDependency())
    private val safeAmountSetting: Setting<Int> = add(NumberSetting("SafeAmount", 5, 1, 100) { safeSetting.isExpanded }.withUnit("%"))

    internal val inRangeSetting: Setting<Boolean> = add(BooleanSetting("InRange", false).markVisibilityDependency())
    private val rangeSetting: Setting<Float> = add(NumberSetting("Range", 8.0f, 1.0f, 16.0f) { inRangeSetting.isExpanded }.withUnit("m"))
    private val allowFriendsSetting: Setting<Boolean> = add(BooleanSetting("AllowFriends", true) { inRangeSetting.isExpanded })

    private val preferencesSetting: Setting<Boolean> = add(BooleanSetting("Preferences", false).markAsGroup().markVisibilityDependency())
    internal val helmetSetting: Setting<AutoArmorHelmetChoice> = add(EnumSetting("Helmet", AutoArmorHelmetChoice.BEST).dependsOn(preferencesSetting))
    private val goldenBootsSetting: Setting<Boolean> = add(BooleanSetting("GoldenBoots", false) { helmetSetting.value != AutoArmorHelmetChoice.GOLD }.dependsOn(preferencesSetting))
    private val piglinRangeSetting: Setting<Float> = add(NumberSetting("PiglinRange", 8.0f, 1.0f, 16.0f).withUnit("m").dependsOn(preferencesSetting))
    private val onlySafeSetting: Setting<Boolean> = add(BooleanSetting("OnlySafe", false).dependsOn(preferencesSetting))
    private val blastSetting: Setting<AutoArmorBlastChoice> = add(EnumSetting("Blast", AutoArmorBlastChoice.LEGS).dependsOn(preferencesSetting))
    private val bindingCurseSetting: Setting<Boolean> = add(BooleanSetting("BindingCurse", false).dependsOn(preferencesSetting))
    private val thornsSetting: Setting<Boolean> = add(BooleanSetting("Thorns", false).dependsOn(preferencesSetting))

    @JvmField
    val elytraSetting: Setting<Boolean> = add(BooleanSetting("Elytra", false).markVisibilityDependency().dependsOn(preferencesSetting))
    private val onlyAirSetting: Setting<Boolean> = add(BooleanSetting("OnlyAir", false).dependsOn(elytraSetting, preferencesSetting))
    private val blastBootsSetting: Setting<Boolean> = add(BooleanSetting("BlastBoots", false).dependsOn(elytraSetting, preferencesSetting))

    private val swapDelayTimer = ElapsedTimer()
    private val lastGlideTimer = ElapsedTimer()
    private val safetyPredicateChangeTimer = ElapsedTimer()
    private val heightPredicateChangeTimer = ElapsedTimer()
    private val jumpToggleTimer = ElapsedTimer()

    private var jumpToggleActive = false
    private var previousJumpDown = false
    private var previousSafetyPredicate = false
    private var previousHeightPredicate = false
    private var skipNextArmorUpdate = false
    private var nearestAdultPiglinDistance: Double = Double.MAX_VALUE

    init {
        piglinRangeSetting.setBoundaryLabel("None", SettingLabelCondition.MAX)
        piglinRangeSetting.addVisibilityPredicate {
            helmetSetting.value.isGold || isGoldenBootsEnabled(this)
        }
        onlySafeSetting.addVisibilityPredicate {
            helmetSetting.value.isNone
        }
        blastSetting.setChangeCallback { swapDelayTimer.clear() }
        elytraSetting.setChangeCallback {
            swapDelayTimer.clear()
            lastGlideTimer.reset()
        }
        helmetSetting.setChangeCallback { swapDelayTimer.clear() }
    }

    private fun shouldPreferGoldenHelmet(): Boolean {
        return helmetSetting.value.isGold && isPiglinWithinRange()
    }

    private fun shouldPreferElytraForCurrentFlight(): Boolean {
        val elytra = elytraFlyModule
        if (elytra != null && elytra.isToggled) {
            if (elytra.modeSetting.value == ElytraFlyModule.ModeChoice.BOUNCE) {
                return elytra.infDurabilitySetting.value.isDisabled
            }
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return false
        if (EntityUtils.hasCollisionBelow(player)) {
            return false
        }

        if (PlayerStateUtils.isInLiquid(player)) {
            if (!PlayerStateUtils.isFallFlying()) {
                if (player.isInLava) {
                    if (!jumpToggleTimer.hasElapsed(200L)) {
                        return true
                    }
                }
                return false
            }
        }

        return jumpToggleActive
    }

    private fun updateNearestAdultPiglinDistance() {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val level = ClientAccess.collectiveMinecraft.level ?: return
        val eyePosition = player.eyePosition
        var nearestDistance = Double.MAX_VALUE

        for (entity in level.entitiesForRendering()) {
            if (entity is Piglin && !entity.isBaby) {
                val bounds = entity.boundingBox
                val piglinDistance = WorldUtils.distanceToBounds(eyePosition, bounds)
                nearestDistance = Math.min(nearestDistance, piglinDistance)
            }
        }

        nearestAdultPiglinDistance = nearestDistance
    }

    private fun shouldRemoveHelmet(): Boolean {
        if (!helmetSetting.value.isNone) return false
        return !onlySafeSetting.value || (previousSafetyPredicate && safetyPredicateChangeTimer.hasElapsed(500L))
    }

    private fun updateArmorSafetyState() {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (player.isFallFlying) {
            lastGlideTimer.reset()
        }

        val height = player.y
        val heightPredicate = WallsVelocityHandler.hasPlayerCollisionAtHeight(height)
        val holeManager = ClientContext.holeManager
        if (previousSafetyPredicate != holeManager.isInHole) {
            safetyPredicateChangeTimer.reset()
        }
        if (previousHeightPredicate != heightPredicate) {
            heightPredicateChangeTimer.reset()
        }
        previousHeightPredicate = heightPredicate
        previousSafetyPredicate = holeManager.isInHole
    }

    private fun isPiglinWithinRange(): Boolean {
        if (piglinRangeSetting.isAtMaximum) {
            return true
        }
        return nearestAdultPiglinDistance <= piglinRangeSetting.value
    }

    private fun hasRelevantPlayerInRange(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val level = ClientAccess.collectiveMinecraft.level ?: return false

        for (otherPlayer in level.players()) {
            if (otherPlayer !== player) {
                val distance = otherPlayer.distanceTo(player)
                if (distance <= rangeSetting.value) {
                    val playerName = otherPlayer.gameProfile.name
                    if (!ClientContext.socialRegistry.isFriendName(playerName)) return true
                    if (!allowFriendsSetting.value) return true
                }
            }
        }
        return false
    }

    fun skipNextArmorUpdate() {
        skipNextArmorUpdate = true
    }

    private fun performArmorUpdates() {
        if (skipNextArmorUpdate) {
            skipNextArmorUpdate = false
            return
        }
        updateNearestAdultPiglinDistance()
        updateArmorSafetyState()

        val currentScreen = ClientAccess.collectiveMinecraft.screen
        if (!isArmorScreenAllowed(currentScreen)) return
        if (!swapDelayTimer.hasElapsed(delaySetting.value.toLong())) return

        if (inRangeSetting.value && !hasRelevantPlayerInRange()) return

        val armorChanges = collectArmorChanges()
        if (armorChanges.isEmpty()) return

        noSlowModule?.prepareForInventoryAction()
        var appliedChanges = 0
        for (change in armorChanges) {
            change.execute()
            appliedChanges++
            if (appliedChanges >= frequencySetting.value) break
        }
        swapDelayTimer.reset()
    }

    private fun collectArmorChanges(): List<ArmorSwapAction> {
        val player = ClientAccess.collectiveMinecraft.player ?: return emptyList()
        val armorChanges = ArrayList<ArmorSwapAction>()

        for (armorKind in ArmorSlot.values()) {
            val equippedInventory = player.inventory
            val equippedSlot = armorKind.inventoryIndex
            val equippedStack = equippedInventory.getItem(equippedSlot)
            if (EnchantmentUtils.hasEnchantment(Enchantments.BINDING_CURSE, equippedStack)) continue

            var bestScore = scoreArmor(equippedStack, armorKind)
            var bestSlot = armorKind.inventoryIndex

            for (candidateSlot in 0 until 36) {
                val candidateStack = player.inventory.getItem(candidateSlot)
                val candidateScore = scoreArmor(candidateStack, armorKind)
                if (candidateScore > bestScore) {
                    bestScore = candidateScore
                    bestSlot = candidateSlot
                }
            }

            if (bestSlot != armorKind.inventoryIndex && bestScore != -1.0) {
                val change = ArmorSwapAction(armorKind, bestSlot)
                change.setAlternative(alternativeSetting.value)
                armorChanges.add(change)
            }
        }

        if (shouldRemoveHelmet() && armorChanges.isEmpty()) {
            val helmetInventory = player.inventory
            val helmetSlot = ArmorSlot.HELMET.inventoryIndex
            val helmetStack = helmetInventory.getItem(helmetSlot)
            if (!helmetStack.isEmpty && !EnchantmentUtils.hasEnchantment(Enchantments.BINDING_CURSE, helmetStack)) {
                if (!InventoryUtils.isInventoryFull()) {
                    val removal = ArmorSwapAction(ArmorSlot.HELMET, -1)
                    removal.setQuickMove(true)
                    armorChanges.add(removal)
                }
            }
        }

        return armorChanges
    }

    private fun shouldUseBlastProtection(armorKind: ArmorSlot): Boolean {
        if (elytraSetting.value) {
            if (blastBootsSetting.value && armorKind == ArmorSlot.BOOTS) return true
        }
        val heightPredicateSettled = !previousHeightPredicate && heightPredicateChangeTimer.hasElapsed(150L)

        return when (blastSetting.value) {
            AutoArmorBlastChoice.NONE -> false
            AutoArmorBlastChoice.ALL -> true
            AutoArmorBlastChoice.LEGS -> armorKind == ArmorSlot.LEGGINGS
            AutoArmorBlastChoice.SMART -> armorKind == ArmorSlot.LEGGINGS || (armorKind == ArmorSlot.BOOTS && heightPredicateSettled)
        }
    }

    private fun scoreArmor(stack: ItemStack, armorKind: ArmorSlot): Double {
        val durability = InventoryUtils.getDurabilityPercent(stack)

        if (safeSetting.value) {
            if (durability < safeAmountSetting.value) {
                return -1.0
            }
        }

        if (stack.`is`(Items.SHIELD)) {
            return -1.0
        }

        if (!bindingCurseSetting.value) {
            if (EnchantmentUtils.hasEnchantment(Enchantments.BINDING_CURSE, stack)) {
                return -1.0
            }
        }

        if (stack.`is`(Items.ELYTRA) && armorKind == ArmorSlot.CHESTPLATE) {
            if (DamageUtils.getDurabilityPercent(stack) <= 1) {
                return -1.0
            }
            var elytraScore = 1.0

            if (elytraSetting.value) {
                if (!onlyAirSetting.value || shouldPreferElytraForCurrentFlight()) {
                    elytraScore *= 9999.0
                }
            }
            if (stack.isEnchanted) {
                elytraScore += EnchantmentUtils.getLevel(Enchantments.UNBREAKING, stack)
                elytraScore += EnchantmentUtils.getLevel(Enchantments.MENDING, stack)
            }

            val flightMode = elytraFlyModule?.activeMode
            if (flightMode is NewGrimElytraFlyHandler) {
                if (flightMode.shouldFallWithChestplate()) {
                    return -1.0
                }
            }
            return elytraScore
        }

        val equippable = stack.get(DataComponents.EQUIPPABLE)
        if (equippable != null && equippable.canBeEquippedBy(EntityType.PLAYER)) {
            if (stack.item !is BlockItem) {
                val equipmentSlot = equippable.slot()
                if (equipmentSlot.index == armorKind.armorIndex) {
                    var armorScore = 0.0
                    if (shouldRemoveHelmet() && equippable.slot() == EquipmentSlot.HEAD) {
                        return -1.0
                    }

                    if (helmetSetting.value.isTurtle && stack.`is`(Items.TURTLE_HELMET)) {
                        armorScore = 9999.0
                    }
                    if (shouldPreferGoldenHelmet() && stack.`is`(Items.GOLDEN_HELMET)) {
                        armorScore = 9999.0
                    }
                    if (isGoldenBootsEnabled(this) && isPiglinWithinRange() && stack.`is`(Items.GOLDEN_BOOTS)) {
                        armorScore = 9999.0
                    }

                    val modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS)
                    if (modifiers != null) {
                        for (entry in modifiers.modifiers()) {
                            if (entry.attribute() == Attributes.ARMOR_TOUGHNESS || entry.attribute() == Attributes.ARMOR) {
                                armorScore += entry.modifier().amount() * 10.0
                            }
                        }
                    }

                    if (stack.isEnchanted) {
                        val preferredProtection = if (shouldUseBlastProtection(armorKind)) Enchantments.BLAST_PROTECTION else Enchantments.PROTECTION
                        val enchantments = stack.enchantments
                        for (entry in enchantments.entrySet()) {
                            val level = entry.intValue
                            val holder = entry.key
                            if (!holder.`is`(EnchantmentTags.CURSE)) {
                                val thorns = holder.`is`(Enchantments.THORNS)
                                if (holder.`is`(preferredProtection)) {
                                    armorScore += level * 3
                                } else if (thorns) {
                                    val penalty = if (thornsSetting.value) 0.0 else 1.0
                                    armorScore -= penalty
                                } else {
                                    armorScore += level.toDouble() * 0.1
                                }
                            }
                        }
                    }
                    return armorScore
                }
            }
        }
        return -1.0
    }

    @EventHandler(priority = 100)
    private fun onLocalPlayerPreTick(event: LocalPlayerPreTickEvent) {
        performArmorUpdates()

        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (PlayerStateUtils.isWearingElytra(player)) {
            if (!player.isFallFlying) {
                if (!EntityUtils.hasCollisionBelow(player)) {
                    if (!swapDelayTimer.hasElapsed(300L) && !lastGlideTimer.hasElapsed(500L)) {
                        PacketSender.sendStartFallFlying()
                        player.startFallFlying()
                    }
                }
            }
        }
    }

    @EventHandler
    private fun onMinecraftSetScreenEvent(event: ScreenChangeEvent.Before) {
        if (!isWorldUnavailable) {
            if (event.newScreen == null && RotationManager.is2b2tMode()) {
                if (!InventoryStateManager.isClickRateLimited(ClientContext.inventoryStateManager)) {
                    val previousScreen = event.previousScreen
                    if (previousScreen is AbstractContainerScreen<*>) {
                        if (previousScreen.menu === ClientAccess.collectiveMinecraft.player?.inventoryMenu) {
                            return
                        }
                    }

                    ClientContext.taskScheduler.scheduleRotationTask({
                        InventoryUtils.clickSlot(0, 0, ClickType.PICKUP)
                        InventoryUtils.clickSlot(0, 0, ClickType.PICKUP)
                    }, 0)
                }
            }
        }
    }

    @EventHandler
    private fun onMinecraftTick(event: MinecraftTickEvent) {
        if (!isWorldUnavailable) {
            updateJumpToggle(this)
        }
    }

    private fun isArmorScreenAllowed(screen: Screen?): Boolean {
        if (screen is ClientScreen) return true
        return if (screen is AbstractContainerScreen<*>) screen is InventoryScreen else true
    }

    companion object {
        private var elytraFlyModule: ElytraFlyModule? = null
        private var noSlowModule: NoSlowModule? = null

        init {
            elytraFlyModule = ClientContext.getModuleOrNull(ElytraFlyModule::class.java)
            noSlowModule = ClientContext.getModuleOrNull(NoSlowModule::class.java)
        }

        private fun isGoldenBootsEnabled(owner: AutoArmorModule): Boolean {
            return owner.goldenBootsSetting.value && owner.goldenBootsSetting.isVisible
        }

        private fun updateJumpToggle(owner: AutoArmorModule) {
            val jumpKey = ClientAccess.collectiveMinecraft.options.keyJump
            val jumpDown = jumpKey.isDown && ClientAccess.collectiveMinecraft.screen == null

            if (ClientContext.playerStateTracker.groundedTicks > 1) {
                owner.jumpToggleActive = false
            } else if (jumpDown && !owner.previousJumpDown) {
                if (owner.jumpToggleActive) {
                    owner.jumpToggleTimer.reset()
                }
                owner.jumpToggleActive = true
                owner.lastGlideTimer.reset()
            }
            owner.previousJumpDown = jumpDown
        }

        @JvmStatic
        fun bindModuleRegistry(registry: ModuleRegistry) {
            elytraFlyModule = registry.getModule(ElytraFlyModule::class.java) as? ElytraFlyModule
            noSlowModule = registry.getModule(NoSlowModule::class.java) as? NoSlowModule
        }
    }
}
