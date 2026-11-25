package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.AuraAttackEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.EventStage
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.managers.SwingInteractionManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.movement.ElytraFlyModule
import me.collective.client.modules.movement.ElytraFlyModule.InfDurabilityChoice
import me.collective.client.modules.movement.ElytraFlyModule.ModeChoice
import me.collective.client.modules.movement.FireworksModule
import me.collective.client.modules.movement.NoSlowModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.settings.options.EntitySortChoice
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.combat.DamageUtils
import me.collective.client.utils.combat.RaycastUtils
import me.collective.client.utils.combat.TargetSelector
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.inventory.ArmorSlot
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.inventory.ItemUtils
import me.collective.client.utils.math.Vec3Utils
import me.collective.client.utils.movement.ControlElytraFlyHandler
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.WorldUtils
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.tags.ItemTags
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.Comparator
import java.util.Objects
import java.util.function.Function
import java.util.function.Predicate

class AutoMaceModule : Module("AutoMace", createDescription(), ModuleCategory.COMBAT) {
    private val sortingSetting: Setting<EntitySortChoice> = add(EnumSetting("Sorting", EntitySortChoice.CROSSHAIR))
    private val rangeSetting: Setting<Float> = add(NumberSetting("Range", 64.0f, 16.0f, 256.0f).withUnit("m"))
    private val heightSetting: Setting<Float> = add(NumberSetting("Height", 15.0f, 5.0f, 30.0f).withUnit("m"))
    private val smartSetting: Setting<Boolean> = add(BooleanSetting("Smart", true))
    private val angleSetting: Setting<Float> = add(NumberSetting("Angle", 75.0f, 45.0f, 90.0f).withUnit("°"))
    val nakedsSetting: Setting<Boolean> = add(BooleanSetting("Nakeds", false))
    private val autoBoostSetting: Setting<Boolean> = add(BooleanSetting("AutoBoost", true))
    private val renderSetting: Setting<Boolean> = add(BooleanSetting("Render", false).markVisibilityDependency())
    internal val colorSetting: Setting<Color> = add(ColorSetting("Color", Color(-4351489, true)).dependsOn(renderSetting))
    private val targetsSetting: Setting<Boolean> = add(BooleanSetting("Targets", false).markAsGroup().markVisibilityDependency())
    private val playersSetting: Setting<Boolean> = add(BooleanSetting("Players", true).dependsOn(targetsSetting))
    private val animalsSetting: Setting<Boolean> = add(BooleanSetting("Animals", false).dependsOn(targetsSetting))
    private val hostilesSetting: Setting<Boolean> = add(BooleanSetting("Hostiles", false).dependsOn(targetsSetting))
    private val neutralsSetting: Setting<Boolean> = add(BooleanSetting("Neutrals", false).dependsOn(targetsSetting))

    private val targetRenderer: AutoMaceTargetRenderer = AutoMaceTargetRenderer(this)
    private val strikePlanner: AutoMaceStrikePlanner = AutoMaceStrikePlanner()
    private val boostTimer: ElapsedTimer = ElapsedTimer()
    private val strikeTimer: ElapsedTimer = ElapsedTimer()
    private val recentFlightTimer: ElapsedTimer = ElapsedTimer()
    private var verticalDisplacement: Float = 0f
    private var savedChestplateSlot: Int = -1
    private var pendingChestSwapSlot: Int = -1
    private var striking: Boolean = false
    private var maceAvailable: Boolean = false
    private var target: Entity? = null

    init {
        nakedsSetting.dependsOn(targetsSetting, playersSetting)
        rangeSetting.setBoundaryLabel("Unlimited", SettingLabelCondition.MAX)
        ClientAccess.eventBus.subscribe(targetRenderer)
    }

    private fun restoreChestSwap() {
        if (pendingChestSwapSlot != -1) {
            val swapSlot = pendingChestSwapSlot
            val chestContainerSlot = ArmorSlot.CHESTPLATE.containerSlot
            InventoryUtils.swapMenuSlots(swapSlot, chestContainerSlot)

            if (PlayerStateUtils.isLocalWearingElytra()) {
                startGliding()
            }

            pendingChestSwapSlot = -1
        }
    }

    private fun hasApproachPath(candidate: Entity): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val eyePosition = player.eyePosition
        val targetPosition = candidate.position()

        val rangeValue = rangeSetting.value
        if (!eyePosition.closerThan(targetPosition, rangeValue.toDouble())) {
            return false
        }

        val targetBounds = candidate.boundingBox.inflate(0.5)
        val solidBlockCount = intArrayOf(0)
        val blocked = BlockGetter.traverseBlocks(
            eyePosition,
            targetPosition,
            solidBlockCount,
            { counts, position ->
                val state = level.getBlockState(position)
                if (targetBounds.intersects(AABB(position))) {
                    null
                } else {
                    val collisionShape = state.getCollisionShape(level, position)
                    if (!collisionShape.isEmpty) {
                        if (++counts[0] > 1) {
                            java.lang.Boolean.TRUE
                        } else if (hasNearbyAirSpace(position)) {
                            null
                        } else {
                            java.lang.Boolean.TRUE
                        }
                    } else {
                        null
                    }
                }
            },
            { java.lang.Boolean.FALSE }
        )
        return blocked != true
    }

    private fun isTargetInvalid(candidate: Entity): Boolean {
        if (!candidate.isAlive) {
            return true
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return true
        val eyePosition = player.eyePosition
        val currentTarget = target ?: return true
        val targetPosition = currentTarget.position()

        val rangeValue = rangeSetting.value
        if (!eyePosition.closerThan(targetPosition, rangeValue.toDouble())) {
            return true
        }

        val level = ClientAccess.collectiveMinecraft.level ?: return true
        val entityId = candidate.id

        if (level.getEntity(entityId) == null) {
            return true
        }

        return ClientContext.socialRegistry.isFriendEntity(candidate)
    }

    fun getFlightRotation(): FloatArray {
        val player = ClientAccess.collectiveMinecraft.player
        val currentTarget = target
        if (currentTarget == null || player == null) {
            return if (player != null) floatArrayOf(player.yRot, player.xRot) else floatArrayOf(0f, 0f)
        }

        val targetPosition = currentTarget.position()
        val eyePosition = player.eyePosition
        val horizontalDistance = Vec3Utils.horizontalDistance(targetPosition, eyePosition)

        val reverseYaw = horizontalDistance < 12.0f
        val offsetPosition = currentTarget.position()

        val targetHeight = currentTarget.bbHeight.toDouble()
        val targetTop = offsetPosition.add(0.0, targetHeight, 0.0)

        val targetRotation = RotationManager.getRotationsToPosition(targetTop)
        if (striking) {
            val strikeRotation = strikePlanner.rotation()
            return strikeRotation ?: targetRotation
        }

        val flightRotation = FloatArray(2)
        val targetYaw = targetRotation[0]
        val yawOffset = if (reverseYaw) 180 else 0

        flightRotation[0] = Mth.wrapDegrees(targetYaw + yawOffset)
        flightRotation[1] = -angleSetting.value
        return flightRotation
    }

    private fun refreshMaceAvailability() {
        val macePredicate = Predicate<ItemStack> { ItemUtils.isMace(it) }
        val maceSlot = InventoryUtils.findSwapSlot(macePredicate, true)
        maceAvailable = maceSlot.isFound
    }

    private fun updateFlightRotation() {
        if (!isFlightControlActive()) {
            strikePlanner.reset()
            return
        }
        val currentTarget = target ?: return
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val heightAboveTarget = player.y - currentTarget.boundingBox.maxY
        val horizontalDistance = Vec3Utils.horizontalDistance(player.position(), currentTarget.position()).toDouble()
        if (heightAboveTarget > heightSetting.value || hasOverheadCollision()) {
            striking = true
        }

        val controlMode = elytraFly.isToggled && elytraFly.modeSetting.value == ModeChoice.CONTROL
        if (striking && smartSetting.value && (PlayerStateUtils.isLocalWearingElytra() || PlayerStateUtils.isFallFlying())) {
            val range = aura.getEffectiveRange(aura.rangeSetting).toDouble()
            if (controlMode) {
                strikePlanner.updateControl(
                    currentTarget,
                    range,
                    (elytraFly.flightSpeedSetting.value + fireworks.getAdditionalBoost(false)).toDouble(),
                    elytraFly.vSpeedSetting.value.toDouble(),
                    elytraFly.glideSetting.value.toDouble()
                )
            } else {
                var boostBudget = Double.POSITIVE_INFINITY
                if (fireworks.isRocketBoostActive) {
                    val movingTarget = if (currentTarget.isPassenger) currentTarget.vehicle!! else currentTarget
                    boostBudget = if (MovementPhysics.getHorizontalSpeedKmh(movingTarget) <= 60.0) 10.0 else 0.0
                }
                strikePlanner.updateNormal(currentTarget, range, true, boostBudget)
            }
        } else {
            strikePlanner.reset()
        }

        if (controlMode && elytraFly.activeMode is ControlElytraFlyHandler) {
            val control = elytraFly.activeMode as ControlElytraFlyHandler
            val verticalInput: Int = if (strikePlanner.isActive) {
                if (strikePlanner.shouldDescend()) -1 else 0
            } else if (striking) {
                if (horizontalDistance < elytraFly.flightSpeedSetting.value * 3.0f) -1 else 0
            } else {
                1
            }
            control.setVerticalInputOverride(verticalInput)
        }

        val rotationManager = ClientContext.rotationManager
        rotationManager.forceRotationOverride()
        rotationManager.requestRotation(getFlightRotation(), 2000, true)
    }

    private fun boostWithRocket() {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (target == null || !PlayerStateUtils.isFallFlying()) {
            return
        }

        if (player.onGround()) {
            return
        }

        if (EntityUtils.hasFirework(player)) {
            return
        }

        if (!boostTimer.hasElapsed(400L)) {
            return
        }

        val previousSlot = InventoryUtils.getSelectedSlot()
        val rocketSlot = InventoryUtils.findSwapSlot(Items.FIREWORK_ROCKET, true)
        if (!rocketSlot.isFound) {
            return
        }
        InventoryUtils.swapTo(rocketSlot)
        val gameMode = ClientAccess.collectiveMinecraft.gameMode
        val useHand = rocketSlot.hand

        gameMode?.useItem(player, useHand)
        val swingManager = ClientContext.swingManager
        swingManager.swing(useHand, SwingAction.INTERACT)
        InventoryUtils.swapBack(previousSlot, rocketSlot)
        boostTimer.reset()
    }

    private fun selectTarget() {
        val priorityComparator = Comparator.comparing<Entity, Int> { getTargetPriority(it) }
        val sortingComparator = sortingSetting.value.comparator
        val comparator = priorityComparator.thenComparing(sortingComparator)

        val range = if (rangeSetting.isAtMaximum) 2048.0f else rangeSetting.value
        val ignoreNakeds = !nakedsSetting.value

        var selector = TargetSelector()
            .range(range)
            .withPlayers(true)
            .ignoreNakeds(ignoreNakeds)
            .filter(Predicate { hasApproachPath(it) })

        selector = selector.withAnimals(animalsSetting.value)
        selector = selector.withNeutrals(neutralsSetting.value)
        selector = selector.withHostiles(hostilesSetting.value)
        selector = selector.sortBy(comparator)

        target = selector.select()
    }

    private fun getTargetPriority(candidate: Entity): Int {
        if (candidate is Player) {
            return if (ClientContext.socialRegistry.isEnemyPlayer(candidate)) 0 else 1
        }
        return 1
    }

    private fun hasOverheadCollision(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        var solidBlocks = 0

        var bounds = player.boundingBox
        bounds = bounds.setMinY(bounds.maxY)
        bounds = bounds.inflate(1.0, 0.0, 1.0)
        val positions = BlockPos.betweenClosed(bounds).iterator()

        while (positions.hasNext()) {
            var position = positions.next()
            position = position.above()

            val collisionShape = level.getBlockState(position).getCollisionShape(level, position)
            if (!collisionShape.isEmpty) {
                solidBlocks++
            }
        }

        return solidBlocks > 3
    }

    override fun getInfo(): String? {
        val currentTarget = target
        return if (currentTarget != null && currentTarget is Player) {
            val name = currentTarget.name.string
            val distance = WorldUtils.getEntityDistance(currentTarget, false)
            String.format("%s, %.1f", name, distance)
        } else {
            null
        }
    }

    override fun onEnable() {
        if (!isWorldUnavailable) {
            selectTarget()
            targetRenderer.reset()
            strikePlanner.reset()
            strikeTimer.clear()
            pendingChestSwapSlot = -1
            savedChestplateSlot = -1
            striking = false
        }
    }

    override fun onDisable() {
        if (isWorldUnavailable) {
            return
        }
        val player = ClientAccess.collectiveMinecraft.player
        var savedSlotInvalid = false
        if (savedChestplateSlot != -1 && player != null) {
            val savedStack = player.inventory.getItem(savedChestplateSlot)
            savedSlotInvalid = !isChestArmor(savedStack)
        }

        if (!autoArmor.isToggled && !savedSlotInvalid) {
            if (savedChestplateSlot != -1) {
                pendingChestSwapSlot = savedChestplateSlot
                savedChestplateSlot = -1
            }
            restoreChestSwap()
        } else {
            pendingChestSwapSlot = -1
            savedChestplateSlot = -1
        }
    }

    private fun refreshTarget() {
        val currentTarget = target
        if (currentTarget != null && isTargetInvalid(currentTarget)) {
            target = null
        }

        if (target == null) {
            selectTarget()
        }

        val player = ClientAccess.collectiveMinecraft.player
        if (player != null && PlayerStateUtils.isInWater(player)) {
            target = null
        }
    }

    @EventHandler(priority = -100)
    fun steerFlightOnTick(event: LocalPlayerPreTickEvent) {
        updateFlightRotation()
    }

    private fun startGliding() {
        ClientAccess.collectiveMinecraft.player?.startFallFlying()
        PacketSender.sendStartFallFlying()
    }

    private fun isChestArmor(stack: ItemStack): Boolean {
        return stack.`is`(ItemTags.CHEST_ARMOR)
    }

    @EventHandler(priority = -100)
    fun finishStrikeStage(event: AuraAttackEvent) {
        if (event.stage != EventStage.Pre) {
            if (striking && maceAvailable && target != null) {
                striking = false
                strikeTimer.reset()
            }
        }
    }

    @EventHandler
    fun onMinecraftDisconnectEvent(event: MinecraftDisconnectEvent) {
        striking = false
        target = null
        strikeTimer.clear()
        strikePlanner.reset()
        targetRenderer.reset()
    }

    @EventHandler
    fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val previousY = player.yo
        val verticalDelta = previousY - player.y
        verticalDisplacement = Math.abs(verticalDelta).toFloat()

        if (PlayerStateUtils.isFallFlying()) {
            recentFlightTimer.reset()
        }
    }

    @EventHandler(priority = 100)
    fun prepareMaceTick(event: LocalPlayerPreTickEvent) {
        refreshMaceAvailability()
        refreshTarget()
        var infiniteDurabilityMode = false

        if (elytraFly.isToggled) {
            infiniteDurabilityMode = !elytraFly.infDurabilitySetting.value.isDisabled
        }

        if (infiniteDurabilityMode || (target != null && maceAvailable)) {
            if (!PlayerStateUtils.isLocalWearingElytra()
                && !striking
                && pendingChestSwapSlot == -1
                && !infiniteDurabilityMode
            ) {
                autoArmor.skipNextArmorUpdate()
                equipElytra()
            } else {
                if (renderSetting.value) {
                    targetRenderer.addTargetMarker(target)
                }

                if (autoBoostSetting.value) {
                    boostWithRocket()
                }

                val currentTarget = target
                val player = ClientAccess.collectiveMinecraft.player
                if (currentTarget != null && player != null) {
                    autoArmor.skipNextArmorUpdate()

                    val targetTopY = currentTarget.boundingBox.maxY
                    val heightAboveTarget = player.y - targetTopY

                    if (!striking) {
                        restoreChestSwap()

                        if (!PlayerStateUtils.isFallFlying()) {
                            if (PlayerStateUtils.isWearingElytra(player)) {
                                if (!EntityUtils.hasCollisionBelow(player)) {
                                    PacketSender.sendStartFallFlying()
                                    player.startFallFlying()
                                }
                            }
                        }

                        if (!PlayerStateUtils.isFallFlying()) {
                            if (player.onGround()) {
                                if (!strikeTimer.hasElapsed(500L)) {
                                    ClientContext.inputOverrides.requestJump(true)
                                }
                            }
                        }
                    } else {
                        if (player.onGround()) {
                            striking = false
                        } else {
                            val playerY = player.y
                            val targetY = currentTarget.y
                            if (playerY < targetY - currentTarget.bbHeight) {
                                striking = false
                            }
                        }

                        val auraRangeValue = aura.rangeSetting.value
                        val visibleTarget = if (Objects.equals(auraRangeValue, aura.wallRangeSetting.value)) {
                            RaycastUtils.hasVisibleEntitySample(currentTarget)
                        } else {
                            true
                        }

                        val effectiveRangeSetting = if (visibleTarget) aura.rangeSetting else aura.wallRangeSetting
                        val attackRange = aura.getEffectiveRange(effectiveRangeSetting)
                        var effectiveRange = attackRange + verticalDisplacement * 2.0f

                        if (noSlow.isGrimStrictInventoryEnabled) {
                            effectiveRange += verticalDisplacement
                        }

                        if (elytraFly.modeSetting.value == ModeChoice.CONTROL) {
                            effectiveRange += elytraFly.vSpeedSetting.value
                        }

                        val withinAttackRange = if (strikePlanner.isActive) {
                            strikePlanner.hitTicks() <= (if (noSlow.isGrimStrictInventoryEnabled) 1 else 0)
                        } else {
                            heightAboveTarget <= effectiveRange
                        }

                        if (withinAttackRange && pendingChestSwapSlot == -1) {
                            if (PlayerStateUtils.isLocalWearingElytra()) {
                                val chestplateSlot = findChestArmorOrEmptySlot()
                                if (chestplateSlot != -1) {
                                    pendingChestSwapSlot = InventoryUtils.toMenuSlot(chestplateSlot)
                                    val chestContainerSlot = ArmorSlot.CHESTPLATE.containerSlot
                                    InventoryUtils.swapMenuSlots(pendingChestSwapSlot, chestContainerSlot)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            restoreChestSwap()
        }
    }

    private fun hasNearbyAirSpace(position: BlockPos): Boolean {
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        var airBlocks = 0
        var bounds = AABB(position)
        bounds = bounds.inflate(0.5, -0.5, 0.5)
        val positions = BlockPos.betweenClosed(bounds).iterator()

        while (positions.hasNext()) {
            val neighbor = positions.next()
            val collisionShape = level.getBlockState(neighbor).getCollisionShape(level, neighbor)
            if (collisionShape.isEmpty) {
                airBlocks++
            }
        }

        return airBlocks > 2
    }

    private fun findChestArmorOrEmptySlot(): Int {
        var slot = InventoryUtils.findInventorySlot(Predicate<ItemStack> { isChestArmor(it) }, false)
        if (slot == -1) {
            slot = InventoryUtils.findInventorySlot(Predicate<ItemStack> { it.isEmpty }, false)
        }
        return slot
    }

    val isStrikingTarget: Boolean @JvmName("isStrikingTargetProp") get() = isStrikingTarget()
    fun isStrikingTarget(): Boolean {
        return target != null && striking
    }

    fun shouldSuppressGrimBoost(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val currentTarget = target
        if (!isToggled || !maceAvailable || currentTarget == null || currentTarget.distanceTo(player) > 10.0f) {
            return false
        }
        val movingTarget = if (currentTarget.isPassenger) currentTarget.vehicle!! else currentTarget
        return MovementPhysics.getHorizontalSpeedKmh(movingTarget) <= 60.0
    }

    fun isFlightControlActive(): Boolean {
        if (target != null && !recentFlightTimer.hasElapsed(100L) && maceAvailable) {
            return true
        }
        return false
    }

    private fun equipElytra() {
        if (savedChestplateSlot == -1) {
            val player = ClientAccess.collectiveMinecraft.player ?: return
            val elytraInventorySlot = InventoryUtils.findInventorySlot(Predicate<ItemStack> { stack ->
                if (stack.`is`(Items.ELYTRA)) {
                    DamageUtils.getDurabilityPercent(stack) > 1
                } else {
                    false
                }
            }, false)

            val elytraContainerSlot = InventoryUtils.toMenuSlot(elytraInventorySlot)
            if (elytraContainerSlot != -1) {
                val chestInventorySlot = ArmorSlot.CHESTPLATE.inventoryIndex
                val previousChestStack = player.inventory.getItem(chestInventorySlot)
                val chestContainerSlot = ArmorSlot.CHESTPLATE.containerSlot
                InventoryUtils.swapMenuSlots(elytraContainerSlot, chestContainerSlot)

                startGliding()
                if (isChestArmor(previousChestStack)) {
                    savedChestplateSlot = elytraContainerSlot
                }
            }
        }
    }

    companion object {
        private lateinit var aura: AuraModule
        private lateinit var elytraFly: ElytraFlyModule
        private lateinit var autoArmor: AutoArmorModule
        private lateinit var noSlow: NoSlowModule
        private lateinit var fireworks: FireworksModule

        @JvmStatic
        fun bindReferencedModules(modules: ModuleRegistry) {
            aura = modules.getModule(AuraModule::class.java)!!
            elytraFly = modules.getModule(ElytraFlyModule::class.java)!!
            autoArmor = modules.getModule(AutoArmorModule::class.java)!!
            noSlow = modules.getModule(NoSlowModule::class.java)!!
            fireworks = modules.getModule(FireworksModule::class.java)!!
        }

        private fun createDescription(): String {
            val warningColor = ChatFormatting.RED.toString()
            return "Automatically flies towards an enemy to smash them.\n${warningColor}Requires Aura module enabled."
        }
    }
}
