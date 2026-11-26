package me.collective.client.modules.combat

import net.minecraft.world.entity.EntityTypes
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.AuraAttackEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.EventStage
import me.collective.client.events.RotationRequestEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.exploit.MultiTaskModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.settings.options.AntiCheatRotationsChoice
import me.collective.client.settings.options.AuraMaceChoice
import me.collective.client.settings.options.AuraSwapChoice
import me.collective.client.settings.options.AuraTypeChoice
import me.collective.client.settings.options.AuraWeaponChoice
import me.collective.client.settings.options.EntitySortChoice
import me.collective.client.settings.options.MultiTaskModeChoice
import me.collective.client.utils.LockUtils
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.combat.RaycastUtils
import me.collective.client.utils.combat.TargetSelector
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.EnchantmentUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.inventory.ItemUtils
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.tags.ItemTags
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.AxeItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TridentItem
import net.minecraft.world.item.component.AttackRange
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.function.Predicate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class AuraModule : Module("Aura", "Attacks entities nearby.", ModuleCategory.COMBAT, "killaura", "forcefield", "ka") {
    private val sortingSetting: Setting<EntitySortChoice>
    private val fovSetting: Setting<Float>
    val delaySetting: Setting<Float>
    val rangeSetting: Setting<Float>
    val wallRangeSetting: Setting<Float>
    val extraRangeSetting: Setting<Boolean>
    val tPSSyncSetting: Setting<Boolean>
    private val pauseBaritoneSetting: Setting<Boolean>
    private val rotateSetting: Setting<Boolean>
    private val grimSafeSetting: Setting<Boolean>
    private val grimMarginSetting: Setting<Float>
    private val magnetSetting: Setting<Boolean>
    private val offsetSetting: Setting<Int>
    private val limitSetting: Setting<Float>
    val renderSetting: Setting<Boolean>
    val typeSetting: Setting<AuraTypeChoice>
    val fillSetting: Setting<Color>
    val outlineSetting: Setting<Color>
    val lineWidthSetting: Setting<Float>
    private val weaponsSetting: Setting<Boolean>
    val weaponSetting: Setting<AuraWeaponChoice>
    private val swapSetting: Setting<AuraSwapChoice>
    private val maceSetting: Setting<AuraMaceChoice>
    private val alternativeMaceSetting: Setting<Boolean>
    val targetsSetting: Setting<Boolean>
    private val playersSetting: Setting<Boolean>
    private val invisibleSetting: Setting<Boolean>
    private val ignoreNakedsSetting: Setting<Boolean>
    private val animalsSetting: Setting<Boolean>
    private val villagersSetting: Setting<Boolean>
    private val hostilesSetting: Setting<Boolean>
    private val neutralsSetting: Setting<Boolean>
    private val crystalsSetting: Setting<Boolean>
    private val projectilesSetting: Setting<Boolean>
    private val vehiclesSetting: Setting<Boolean>
    private val ignoreNamedSetting: Setting<Boolean>
    private val attackCooldown: AuraAttackCooldown
    private val attackReadiness: AuraAttackReadiness
    private val targetRenderer: AuraTargetRenderer
    private var restoreSlot = -1
    var criticalAttackFlag = false
    private var suppressCriticals = false
    var target: Entity? = null
        @JvmName("getTargetProp") get
        @JvmName("setTargetProp") set
    private var crosshairTarget: Entity? = null

    init {
        this.sortingSetting = this.add(EnumSetting("Sorting", EntitySortChoice.DISTANCE))
        this.fovSetting = this.add(NumberSetting("Fov", 180.0f, 0.0f, 180.0f).withUnit("°"))
        this.delaySetting = this.add(NumberSetting("Delay", 1.0f, 0.0f, 1.0f))
        this.rangeSetting = this.add(NumberSetting("Range", 6.5f, 1.0f, 6.5f).withUnit("m"))
        this.wallRangeSetting = this.add(NumberSetting("WallRange", 6.5f, 0.0f, 6.5f).withUnit("m"))
        this.extraRangeSetting = this.add(BooleanSetting("ExtraRange", false))
        this.tPSSyncSetting = this.add(BooleanSetting("TPSSync", false))

        val pauseBaritoneVisibility = Predicate<Any?> { ClientContext.baritoneBridge.isAvailable() }
        this.pauseBaritoneSetting = this.add(BooleanSetting("PauseBaritone", false, pauseBaritoneVisibility))
        this.rotateSetting = this.add(BooleanSetting("Rotate", false).markVisibilityDependency())
        this.grimSafeSetting = this.add(BooleanSetting("GrimSafe", false))
        this.grimMarginSetting = this.add(NumberSetting("GrimMargin", 0.2f, 0.0f, 0.5f).withUnit("m").dependsOn(this.grimSafeSetting))
        this.magnetSetting = this.add(BooleanSetting("Magnet", false).dependsOn(this.rotateSetting))
        this.offsetSetting = this.add(NumberSetting("Offset", 180, -90, 180).withUnit("°").dependsOn(this.magnetSetting, this.rotateSetting))
        this.extraRangeSetting.addVisibilityPredicate { !this.isGrimSafe }
        this.magnetSetting.addVisibilityPredicate { !this.isGrimSafe }
        this.limitSetting = this.add(NumberSetting("Limit", 1.0f, 0.0f, 1.0f).dependsOn(this.rotateSetting))
        this.renderSetting = this.add(BooleanSetting("Render", false).markVisibilityDependency())
        this.typeSetting = this.add(EnumSetting("Type", AuraTypeChoice.BOX).dependsOn(this.renderSetting))

        val fillVisibility = Predicate<Any?> { this.renderSetting.isExpanded }
        this.fillSetting = this.add(ColorSetting("Fill", Color(1270716927, true), fillVisibility))

        val outlineVisibility = Predicate<Any?> { this.renderSetting.isExpanded }
        this.outlineSetting = this.add(ColorSetting("Outline", Color(-759326209, true), outlineVisibility))

        val lineWidthVisibility = Predicate<Any?> { this.renderSetting.isExpanded }
        this.lineWidthSetting = this.add(NumberSetting("LineWidth", 1.0f, 0.1f, 2.0f, lineWidthVisibility))

        this.weaponsSetting = this.add(BooleanSetting("Weapons", false).markAsGroup().markVisibilityDependency())
        this.weaponSetting = this.add(EnumSetting("Weapon", AuraWeaponChoice.REQUIRE).dependsOn(this.weaponsSetting))

        val swapVisibility = Predicate<Any?> { this.weaponSetting.value == AuraWeaponChoice.SWAP }
        this.swapSetting = this.add(EnumSetting("Swap", AuraSwapChoice.NORMAL, swapVisibility).dependsOn(this.weaponsSetting))
        this.maceSetting = this.add(EnumSetting("Mace", AuraMaceChoice.NONE).dependsOn(this.weaponsSetting))
        this.alternativeMaceSetting = this.add(BooleanSetting("AlternativeMace", false).dependsOn(this.weaponsSetting))

        this.targetsSetting = this.add(BooleanSetting("Targets", true).markAsGroup().markVisibilityDependency())
        this.playersSetting = this.add(BooleanSetting("Players", true).markVisibilityDependency().dependsOn(this.targetsSetting))
        this.invisibleSetting = this.add(BooleanSetting("Invisible", true).dependsOn(this.targetsSetting, this.playersSetting))
        this.ignoreNakedsSetting = this.add(BooleanSetting("IgnoreNakeds", false).dependsOn(this.targetsSetting, this.playersSetting))

        val animalsVisibility = Predicate<Any?> { this.targetsSetting.isExpanded }
        this.animalsSetting = this.add(BooleanSetting("Animals", false, animalsVisibility))
        this.villagersSetting = this.add(BooleanSetting("Villagers", false).dependsOn(this.targetsSetting))
        this.hostilesSetting = this.add(BooleanSetting("Hostiles", true).dependsOn(this.targetsSetting))
        this.neutralsSetting = this.add(BooleanSetting("Neutrals", false).dependsOn(this.targetsSetting))
        this.crystalsSetting = this.add(BooleanSetting("Crystals", false).dependsOn(this.targetsSetting))
        this.projectilesSetting = this.add(BooleanSetting("Projectiles", false).dependsOn(this.targetsSetting))
        this.vehiclesSetting = this.add(BooleanSetting("Vehicles", false).dependsOn(this.targetsSetting))
        this.ignoreNamedSetting = this.add(BooleanSetting("IgnoreNamed", false).dependsOn(this.targetsSetting))

        this.attackCooldown = AuraAttackCooldown(this)
        this.attackReadiness = AuraAttackReadiness(this)
        this.targetRenderer = AuraTargetRenderer(this)
        this.restoreSlot = -1
        this.rangeSetting.setBoundaryLabel("Vanilla", SettingLabelCondition.MAX)
        this.wallRangeSetting.setBoundaryLabel("Vanilla", SettingLabelCondition.MAX)
        this.fovSetting.setBoundaryLabel("OnSight", SettingLabelCondition.MIN)
        this.limitSetting.setBoundaryLabel("None", SettingLabelCondition.MIN)
        this.pauseBaritoneSetting.hideWithoutBaritone()
        ClientAccess.eventBus.subscribe(this.targetRenderer)
    }

    private fun isWithinFov(candidate: Entity): Boolean {
        if (this.fovSetting.isAtMinimum) {
            return candidate == this.crosshairTarget
        }
        val angles = RotationManager.getRotationsToEntity(candidate)
        val targetYaw = angles[0]
        val player = ClientAccess.collectiveMinecraft.player ?: return false

        val playerYaw = player.yRot
        val yawDifference = Mth.degreesDifferenceAbs(targetYaw, playerYaw)

        val pitchDelta = player.xRot - angles[1]
        val pitchDifference = abs(pitchDelta)
        val fovLimit = this.fovSetting.value / 2.0f
        return yawDifference <= fovLimit && pitchDifference <= fovLimit
    }

    private fun isSwapWeaponMode(): Boolean {
        return this.weaponSetting.value == AuraWeaponChoice.SWAP
    }

    val isGrimSafe: Boolean
        get() = this.grimSafeSetting.value

    private fun isRotationEnabled(): Boolean {
        return this.rotateSetting.value || this.isGrimSafe
    }

    private fun isMagnetEnabled(): Boolean {
        return this.magnetSetting.value && this.rotateSetting.value && !this.isGrimSafe
    }

    private fun is2b2tBehaviorEnabled(): Boolean {
        return antiCheat.is2b2tEnabled && !this.isGrimSafe
    }

    private fun isExtraRangeEnabled(): Boolean {
        return this.extraRangeSetting.value && !this.isGrimSafe
    }

    fun shouldSuppressCriticals(): Boolean {
        return this.suppressCriticals
    }

    private fun getAimBounds(entity: Entity): AABB {
        if (this.isGrimSafe && entity is LivingEntity) {
            return BoundingBoxUtils.getInterpolationBounds(entity)
        }
        return entity.boundingBox
    }

    private fun getAttackDistance(target: Entity): Double {
        if (!this.isGrimSafe || target is EnderDragon) {
            return WorldUtils.getEntityDistance(target, this.isExtraRangeEnabled())
        }

        val range = WorldUtils.getEntityRange(target, false)
        val origin = this.adjustAimOrigin(range.origin)
        return WorldUtils.distanceToBounds(origin, this.getAimBounds(target))
    }

    val attackDelay: Float @JvmName("getAttackDelayProp") get() = getAttackDelay()

    fun getAttackDelay(): Float {
        val currentTarget = this.target ?: return this.delaySetting.value

        val maceSlot = this.findMaceSlot(currentTarget)
        if (maceSlot.isFound) {
            val fallDistance = PlayerStateUtils.getTrackedFallDistance()
            if (fallDistance > 1.0f) {
                val inventory = ClientAccess.collectiveMinecraft.player?.inventory ?: return this.delaySetting.value
                return if (maceSlot.slot == inventory.selectedSlot) 0.1f else 0.25f
            }
        }

        return this.delaySetting.value
    }

    private fun restoreWeaponSlot() {
        if (this.isSwapWeaponMode() && this.swapSetting.value == AuraSwapChoice.SWAPBACK && this.restoreSlot != -1) {
            InventoryUtils.selectHotbarSlotForPlacement(this.restoreSlot)
            this.restoreSlot = -1
        }
    }

    fun isTargetWithinAttackRange(): Boolean {
        val currentTarget = this.target ?: return false
        val distance = this.getAttackDistance(currentTarget)
        val visible = RaycastUtils.hasVisibleEntitySample(currentTarget)
        val attackRange = this.getEffectiveRange(if (visible) this.rangeSetting else this.wallRangeSetting)
        return distance <= attackRange
    }

    fun getTarget(): Entity? {
        return this.target
    }

    private fun updateCrosshairTarget() {
        if (!this.fovSetting.isAtMinimum) {
            this.crosshairTarget = null
        } else {
            val hit = ClientAccess.collectiveMinecraft.hitResult
            if (hit is EntityHitResult) {
                this.crosshairTarget = hit.entity
            } else {
                this.crosshairTarget = null
            }
        }
    }

    private fun findWeaponSlot(): InventoryUtils.SwapSlot {
        if (!this.isSwapWeaponMode()) {
            return InventoryUtils.SwapSlot(InventoryUtils.getSelectedSlot(), true, InteractionHand.MAIN_HAND)
        }
        val weaponPredicate = Predicate<ItemStack> { stack ->
            ItemUtils.isAxe(stack) || ItemUtils.isSword(stack) || ItemUtils.isSpear(stack)
        }
        return InventoryUtils.findSwapSlot(weaponPredicate, false)
    }

    override fun getInfo(): String? {
        val currentTarget = this.target
        return if (currentTarget is Player) {
            currentTarget.name.string
        } else {
            null
        }
    }

    override fun onToggle() {
        this.target = null
        ClientContext.baritoneBridge.releasePause(this)
    }

    override fun onDisable() {
        if (this.isWorldUnavailable) {
            this.restoreSlot = -1
        } else {
            this.restoreWeaponSlot()
        }
    }

    private fun attackCurrentTarget() {
        val currentTarget = this.target ?: return
        val attackRange = if (RaycastUtils.hasVisibleEntitySample(currentTarget)) {
            this.getEffectiveRange(this.rangeSetting)
        } else {
            this.getEffectiveRange(this.wallRangeSetting)
        }

        if (this.getAttackDistance(currentTarget) > attackRange) {
            return
        }

        val originalSlot = InventoryUtils.getSelectedSlot()
        var weaponSlot = this.findWeaponSlot()
        var swapRequired = this.isSwapWeaponMode()

        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (currentTarget is EndCrystal) {
            swapRequired = player.hasEffect(MobEffects.WEAKNESS)
            if (swapRequired) {
                swapRequired = !player.hasEffect(MobEffects.STRENGTH)
            }
        }

        var silentSwap = this.swapSetting.value == AuraSwapChoice.SILENT && swapRequired
        val maceSlot = this.findMaceSlot(currentTarget)
        if (maceSlot.isFound) {
            weaponSlot = maceSlot
            swapRequired = true
            silentSwap = true
        }

        val attackStrength = this.attackCooldown.getAttackStrength(
            if (weaponSlot.isFound) weaponSlot.stack else player.mainHandItem
        )
        if (!this.attackReadiness.canAttack(currentTarget, attackStrength)) {
            return
        }

        val inventorySwap = weaponSlot.isFound && !weaponSlot.hotbar && !weaponSlot.isInHand
        if (swapRequired && inventorySwap && player.containerMenu != player.inventoryMenu) {
            return
        }

        if (swapRequired) {
            if (multiTask.shouldPauseModule(this)) {
                if (player.usedItemHand == InteractionHand.MAIN_HAND && weaponSlot.slot != originalSlot && weaponSlot.isFound) {
                    return
                }
            }

            InventoryUtils.swapTo(weaponSlot)
            if (originalSlot != weaponSlot.slot) {
                this.restoreSlot = originalSlot
            }
        }

        try {
            ClientAccess.eventBus.post(AuraAttackEvent(EventStage.Pre, currentTarget.id))
            this.sendAttack(currentTarget)

            val useMagnet = this.isMagnetEnabled() && currentTarget.type != EntityTypes.CREEPER && currentTarget.type != EntityTypes.END_CRYSTAL
            if (useMagnet) {
                val rotations = RotationManager.getRotationsToEntity(currentTarget)
                val targetYaw = rotations[0]
                val offsetYaw = targetYaw + this.offsetSetting.value
                rotations[0] = Mth.wrapDegrees(offsetYaw)
                ClientContext.rotationManager.requestRotation(rotations, 4)
            }
        } finally {
            if (silentSwap && weaponSlot.isFound) {
                InventoryUtils.swapBack(originalSlot, weaponSlot)
            }
        }
        ClientAccess.eventBus.post(AuraAttackEvent(EventStage.Post, currentTarget.id))
    }

    private fun getWeaponAttackRange(): Float {
        val weaponSlot = this.findWeaponSlot()
        val player = ClientAccess.collectiveMinecraft.player
        val weaponStack = if (weaponSlot.isFound) weaponSlot.stack else player?.mainHandItem ?: ItemStack.EMPTY
        val attackRange = weaponStack.get(DataComponents.ATTACK_RANGE)
        return attackRange?.maxReach() ?: 3.0f
    }

    fun getEffectiveRange(rangeSetting: Setting<Float>): Float {
        if (!this.isGrimSafe && !rangeSetting.isAtMaximum) {
            return rangeSetting.value
        }

        val weaponRange = this.getWeaponAttackRange()
        val configuredRange = if (rangeSetting.isAtMaximum) weaponRange else rangeSetting.value
        if (!this.isGrimSafe) {
            return configuredRange
        }

        val safeRange = max(0.0f, weaponRange - this.grimMarginSetting.value)
        return min(configuredRange, safeRange)
    }

    private fun shouldAttackDuringRotationCollection(): Boolean {
        return autoMace.isToggled && autoMace.isStrikingTarget
            && RotationManager.isMovementSyncEnabled()
            && antiCheat.rotationsSetting.value == AntiCheatRotationsChoice.MOTION
    }

    @EventHandler
    private fun onPlayerUpdate(event: RotationRequestEvent) {
        this.updateCrosshairTarget()
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val targetDead = this.target?.isAlive == false
        val multiTaskBlocked = multiTask.shouldPauseModule(this) && multiTask.mode == MultiTaskModeChoice.NONE

        val heldItem = player.mainHandItem.item
        var missingWeapon = !this.isAllowedWeapon(heldItem) && this.weaponSetting.value == AuraWeaponChoice.REQUIRE
        if (autoMace.isToggled && autoMace.isStrikingTarget) {
            missingWeapon = false
        }

        if (missingWeapon || targetDead || multiTaskBlocked) {
            this.target = null
            return
        }

        if (player.containerMenu != player.inventoryMenu && this.is2b2tBehaviorEnabled()) {
            return
        }

        var searchPadding = 0.0f
        if (this.limitSetting.isAtMaximum && RotationManager.isMovementSyncEnabled()
            && (!PlayerStateUtils.isFallFlying() || RotationManager.isSilentRotationMode())) {
            searchPadding = 0.1f
        }

        val targetFilter = this.buildTargetFilter(searchPadding)
        this.target = targetFilter.select()
        ClientContext.baritoneBridge.releasePause(this)

        if (this.pauseBaritoneSetting.value && this.target != null) {
            ClientContext.baritoneBridge.requestPause(this)
        }

        val currentTarget = this.target
        if (currentTarget == null) {
            this.restoreWeaponSlot()
            return
        }

        val attackStrength = this.attackCooldown.getSlotAttackStrength(this.findWeaponSlot())

        if (RotationManager.isMovementSyncEnabled()) {
            if (!this.attackReadiness.canAttack(currentTarget, attackStrength)) {
                return
            }
        }

        if (this.shouldAttackDuringRotationCollection()) {
            ClientContext.rotationManager.executeAction { this.attackCurrentTarget() }
        } else {
            LockUtils.runWithSharedLock { this.attackCurrentTarget() }
        }

        if (!this.isRotationEnabled()) {
            return
        }

        if (!RotationManager.isMovementSyncEnabled()) {
            if (this.limitSetting.value != 0.0f) {
                val limit = this.limitSetting.value
                if (limit * this.getAttackDelay() > attackStrength) {
                    return
                }
            }
        }

        val rangeResult = WorldUtils.getEntityRange(currentTarget, this.isExtraRangeEnabled())
        val rawAimOrigin = rangeResult.origin
        val aimOrigin = this.adjustAimOrigin(rawAimOrigin)

        val targetBounds = this.getAimBounds(currentTarget)
        val aimPoint = if (this.isGrimSafe && currentTarget !is EnderDragon) {
            targetBounds.center
        } else {
            BoundingBoxUtils.clampToBounds(aimOrigin, targetBounds)
        }

        val rotations = RotationManager.calculateRotations(aimOrigin, aimPoint)
        ClientContext.rotationManager.requestRotation(rotations, 5, this.isGrimSafe)
    }

    private fun buildTargetFilter(searchPadding: Float): TargetSelector {
        val filter = TargetSelector()
            .withPlayers(this.playersSetting.value)
            .withNeutrals(this.neutralsSetting.value)
            .withAnimals(this.animalsSetting.value)
            .withVillagers(this.villagersSetting.value)
            .withHostiles(this.hostilesSetting.value)
            .withCrystals(this.crystalsSetting.value)
            .withProjectiles(this.projectilesSetting.value)
            .withVehicles(this.vehiclesSetting.value)

        val wallSearchRange = this.getEffectiveRange(this.wallRangeSetting) + searchPadding
        filter.wallRange(wallSearchRange)

        val visibleSearchRange = this.getEffectiveRange(this.rangeSetting) + searchPadding
        filter.visibleRange(visibleSearchRange)
            .ignoreNamed(this.ignoreNamedSetting.value)
            .ignoreNakeds(this.ignoreNakedsSetting.value)
            .ignoreInvisibles(!this.invisibleSetting.value)

        filter.sortBy(this.sortingSetting.value.comparator).filter { this.isWithinFov(it) }
        return filter.useExtraRange(this.isExtraRangeEnabled())
    }

    private fun findMaceSlot(candidate: Entity): InventoryUtils.SwapSlot {
        if (candidate is Player) {
            if (!PlayerStateUtils.hasArmor(candidate)) {
                if (!autoMace.nakedsSetting.value) {
                    return NO_MACE
                }
            }
        }

        if (autoMace.isToggled && autoMace.isStrikingTarget) {
            return InventoryUtils.findSwapSlot({ ItemUtils.isMace(it) }, this.alternativeMaceSetting.value)
        }

        val player = ClientAccess.collectiveMinecraft.player
        val densityAllowed = player != null && !player.isFallFlying && PlayerStateUtils.getTrackedFallDistance() > 2.0f
        val configuredMacePredicate = Predicate<ItemStack> { candidateStack ->
            ItemUtils.isMace(candidateStack) && this.maceSetting.value.matches(candidateStack)
        }
        val maceSlot = InventoryUtils.findSwapSlot(configuredMacePredicate, this.alternativeMaceSetting.value)
        if (maceSlot.isFound) {
            val maceStack = maceSlot.stack
            val densityEnchantment = Enchantments.DENSITY
            if (EnchantmentUtils.hasEnchantment(densityEnchantment, maceStack) && !densityAllowed) {
                return NO_MACE
            }
        }

        if (this.maceSetting.value == AuraMaceChoice.SMART) {
            val smartFallDistance = PlayerStateUtils.getTrackedFallDistance()
            if (smartFallDistance > 3.0f && !maceSlot.isFound) {
                return InventoryUtils.findSwapSlot({ ItemUtils.isMace(it) }, this.alternativeMaceSetting.value)
            }
        }

        return maceSlot
    }

    private fun adjustAimOrigin(origin: Vec3?): Vec3 {
        val baseOrigin = origin ?: (ClientAccess.collectiveMinecraft.player?.eyePosition ?: Vec3.ZERO)
        if (RotationManager.isMovementSyncEnabled() && !RotationManager.isSilentRotationMode()) {
            val motionOffset = ClientContext.movementPredictionManager.adjustedLocalMotion
            return baseOrigin.add(motionOffset)
        }
        return baseOrigin
    }

    private fun isAllowedWeapon(item: Item): Boolean {
        val stack = item.defaultInstance
        if (ItemUtils.isSword(stack) || item is TridentItem || item is AxeItem || ItemUtils.isMace(stack) || ItemUtils.isSpear(stack)) {
            return true
        }
        if (!stack.`is`(ItemTags.PICKAXES)) {
            return false
        }
        val alternativeWeaponPredicate = Predicate<ItemStack> { candidateStack ->
            ItemUtils.isSword(candidateStack) || candidateStack.item is AxeItem
        }
        return InventoryUtils.findHotbarSlot(alternativeWeaponPredicate) == -1
    }

    private fun sendAttack(target: Entity) {
        val previousSuppressCriticals = this.suppressCriticals
        if (this.isGrimSafe) {
            this.suppressCriticals = true
        }

        try {
            val player = ClientAccess.collectiveMinecraft.player ?: return
            val wasSprinting = player.isSprinting
            val desiredSprint = !this.isMagnetEnabled()
            var changeSprint = desiredSprint == wasSprinting
            if (this.is2b2tBehaviorEnabled() && RotationManager.isMovementSyncEnabled() && wasSprinting && changeSprint) {
                changeSprint = false
            }
            if (target.type == EntityTypes.END_CRYSTAL) {
                changeSprint = false
            }
            if (changeSprint) {
                val action = if (wasSprinting) ServerboundPlayerCommandPacket.Action.STOP_SPRINTING else ServerboundPlayerCommandPacket.Action.START_SPRINTING
                PacketSender.sendPlayerCommand(action)
            }
            BlockInteractionUtils.attackEntity(target)
            if (changeSprint) {
                val action = if (wasSprinting) ServerboundPlayerCommandPacket.Action.START_SPRINTING else ServerboundPlayerCommandPacket.Action.STOP_SPRINTING
                PacketSender.sendPlayerCommand(action)
            }
            if (!PlayerStateUtils.isFallFlying()) {
                player.attack(target)
            }

            player.resetAttackStrengthTicker()
        } finally {
            this.suppressCriticals = previousSuppressCriticals
        }
    }

    companion object {
        private val NO_MACE = InventoryUtils.SwapSlot(-1, false, InteractionHand.MAIN_HAND)

        lateinit var antiCheat: AntiCheatModule
        lateinit var multiTask: MultiTaskModule
        lateinit var autoMace: AutoMaceModule

        @JvmStatic
        fun bindModuleRegistry(modules: ModuleRegistry) {
            antiCheat = modules.getModule(AntiCheatModule::class.java) as AntiCheatModule
            multiTask = modules.getModule(MultiTaskModule::class.java) as MultiTaskModule
            autoMace = modules.getModule(AutoMaceModule::class.java) as AutoMaceModule
            AuraAttackReadiness.bindModuleRegistry(modules)
        }
    }
}
