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
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.exploit.MultiTaskModule
import me.collective.client.render.FeatureBoxRenderer
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.BlockInteractionUtils
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.piston.PistonBaseBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import java.awt.Color
import java.util.function.Predicate

class PistonCrystalModule : Module("PistonCrystal", "Pushes crystals into your enemies to explode them.", ModuleCategory.COMBAT) {
    private val delaySetting: Setting<Int>
    val placeRangeSetting: Setting<Float>
    val attackRangeSetting: Setting<Float>
    private val targetRangeSetting: Setting<Float>
    val airPlaceSetting: Setting<Boolean>
    val rotateSetting: Setting<Boolean>
    private val alternativeSetting: Setting<Boolean>
    private val renderSetting: Setting<Boolean>
    private val fillSetting: Setting<Color>
    private val outlineSetting: Setting<Color>
    private val fadeSetting: Setting<Boolean>
    private val fadeTimeSetting: Setting<Float>
    private val planner: PistonCrystalPlanner
    internal val stageTracker: PistonCrystalStageTracker
    private val pistonTimer = ElapsedTimer()
    private val crystalTimer = ElapsedTimer()
    private val forcedCrystalTimer = ElapsedTimer()
    private val cycleTimer = ElapsedTimer()
    var powerItem: Item = Items.AIR
        @JvmName("getPowerItemProp") get
        @JvmName("setPowerItemProp") set
    private var target: Player? = null
    private var plan: PistonCrystalPlan? = null
    private var pistonPlaced = false
    private var crystalPlaced = false
    private var powerPlaced = false
    private var forceCrystal = false
    private var wasBlocked = false

    init {
        this.delaySetting = this.add(NumberSetting("Delay", 500, 0, 1000).withUnit("ms"))
        this.placeRangeSetting = this.add(NumberSetting("PlaceRange", 4.5f, 0.1f, 6.0f))
        this.attackRangeSetting = this.add(NumberSetting("AttackRange", 4.0f, 0.1f, 6.0f))
        this.targetRangeSetting = this.add(NumberSetting("TargetRange", 10.0f, 1.0f, 20.0f))
        this.airPlaceSetting = this.add(BooleanSetting("AirPlace", false))
        this.rotateSetting = this.add(BooleanSetting("Rotate", true))
        this.alternativeSetting = this.add(BooleanSetting("Alternative", false))
        this.renderSetting = this.add(BooleanSetting("Render", true).markVisibilityDependency())
        this.fillSetting = this.add(ColorSetting("Fill", Color(1136499199, true)).dependsOn(this.renderSetting))
        this.outlineSetting = this.add(ColorSetting("Outline", Color(-910321153, true)).dependsOn(this.renderSetting))
        this.fadeSetting = this.add(BooleanSetting("Fade", true).markVisibilityDependency().dependsOn(this.renderSetting))
        this.fadeTimeSetting = this.add(NumberSetting("FadeTime", 0.5f, 0.1f, 1.0f).withUnit("s").dependsOn(this.renderSetting, this.fadeSetting))
        this.planner = PistonCrystalPlanner(this)
        this.stageTracker = PistonCrystalStageTracker()

        ClientContext.featureBoxRenderer.register(
            FeatureBoxRenderer.RenderConfiguration(
                this, this.fillSetting, this.outlineSetting, { 1.0f }, this.fadeTimeSetting, { true }, this.fadeSetting, 1000
            )
        )
    }

    override fun getInfo(): String? {
        return this.target?.gameProfile?.name
    }

    override fun onToggle() {
        this.resetStages()
        this.stageTracker.reset()
    }

    @EventHandler
    private fun onRotationRequest(event: RotationRequestEvent) {
        if (this.isWorldUnavailable || ClientAccess.collectiveMinecraft.gameMode == null) {
            return
        }
        this.stageTracker.update()
        this.selectPowerItem()
        this.target = this.selectTarget()
        val currentTarget = this.target
        if (currentTarget == null) {
            this.plan = null
            this.resetStages()
            return
        }
        if (multiTask.shouldPauseModule(this)) {
            return
        }
        this.plan = this.planner.findPlan(currentTarget)
        val currentPlan = this.plan ?: return

        if (this.pistonPlaced && this.crystalPlaced && this.powerPlaced
            && this.cycleTimer.hasElapsed(this.delaySetting.value.toLong())) {
            this.resetStages()
        }

        if (this.isBlocked(currentPlan)) {
            this.attackCrystals(true, currentPlan)
            if (this.forceCrystal) {
                this.placeCrystal(true, currentPlan)
            }
            this.wasBlocked = true
        } else {
            if (this.wasBlocked) {
                this.resetStages()
                this.wasBlocked = false
            }
            this.attackCrystals(false, currentPlan)
            this.placePiston(currentPlan)
            this.placePower(currentPlan)
            this.placeCrystal(false, currentPlan)
        }
    }

    private fun selectTarget(): Player? {
        var selected: Player? = null
        var distance = this.targetRangeSetting.value.toDouble()
        val level = ClientAccess.collectiveMinecraft.level ?: return null
        val localPlayer = ClientAccess.collectiveMinecraft.player ?: return null

        for (candidate in level.players()) {
            if (candidate == localPlayer || candidate.isSpectator || candidate.health <= 0.0f
                || ClientContext.socialRegistry.isFriendPlayer(candidate) || !EntityUtils.hasCollisionBelow(candidate)) {
                continue
            }
            val candidateDistance = localPlayer.eyePosition.distanceTo(candidate.position())
            if (candidateDistance <= distance) {
                selected = candidate
                distance = candidateDistance
            }
        }
        return selected
    }

    private fun placePiston(currentPlan: PistonCrystalPlan) {
        if (this.pistonPlaced) {
            return
        }
        val level = ClientAccess.collectiveMinecraft.level ?: return
        if (level.getBlockState(currentPlan.pistonPosition).`is`(Blocks.PISTON)) {
            this.pistonPlaced = true
            this.pistonTimer.reset()
            return
        }

        var rotations: FloatArray? = null
        if (!this.rotateSetting.value || (this.airPlaceSetting.value && RotationManager.isMovementSyncEnabled())) {
            rotations = floatArrayOf(Mth.wrapDegrees(currentPlan.pistonFacing.toYRot() - 180.0f), 0.0f)
        }

        if (this.placeItem(Items.PISTON, currentPlan.pistonHit, rotations)) {
            if (this.renderSetting.value) {
                ClientContext.featureBoxRenderer.addBox(this, currentPlan.pistonPosition)
            }
            this.pistonPlaced = true
            this.pistonTimer.reset()
            this.stageTracker.markPiston(currentPlan.pistonPosition, currentPlan.pistonFacing)
        }
    }

    private fun placeCrystal(force: Boolean, currentPlan: PistonCrystalPlan) {
        if ((!force && (!this.powerPlaced || this.crystalPlaced))
            || (force && !this.forcedCrystalTimer.hasElapsed(500L))) {
            return
        }
        if (this.hasBlockingEntities(AABB(currentPlan.crystalPosition)) { this.isOldEntity(it) }) {
            return
        }
        if (this.placeItem(Items.END_CRYSTAL, currentPlan.crystalHit, null)) {
            this.crystalPlaced = true
            this.crystalTimer.reset()
            this.stageTracker.markCrystal(currentPlan.crystalPosition)
            if (force) {
                this.forcedCrystalTimer.reset()
            }
        }
    }

    private fun placePower(currentPlan: PistonCrystalPlan) {
        if (this.pistonPlaced && !this.powerPlaced && this.placeItem(this.powerItem, currentPlan.powerHit, null)) {
            if (this.renderSetting.value) {
                ClientContext.featureBoxRenderer.addBox(this, currentPlan.powerPosition)
            }
            this.stageTracker.markPower(currentPlan.powerPosition)
            this.powerPlaced = true
            this.cycleTimer.reset()
        }
    }

    private fun attackCrystals(force: Boolean, currentPlan: PistonCrystalPlan) {
        if (!force && !this.powerPlaced) {
            return
        }
        var selected: EndCrystal? = null
        var distance = this.attackRangeSetting.value.toDouble()
        val level = ClientAccess.collectiveMinecraft.level ?: return
        val localPlayer = ClientAccess.collectiveMinecraft.player ?: return

        for (entity in level.entitiesForRendering()) {
            if (entity !is EndCrystal) {
                continue
            }
            if (!force && entity.x == currentPlan.crystalPosition.x + 0.5 && entity.z == currentPlan.crystalPosition.z + 0.5) {
                continue
            }
            if (!entity.boundingBox.intersects(this.crystalBounds(currentPlan.crystalPosition))
                && (!force || !entity.boundingBox.intersects(AABB(currentPlan.pistonPosition)))) {
                continue
            }
            val candidateDistance = localPlayer.eyePosition.distanceTo(entity.position())
            if (candidateDistance <= distance) {
                selected = entity
                distance = candidateDistance
            }
        }
        if (selected != null) {
            if (this.rotateSetting.value) {
                ClientContext.rotationManager.requestRotation(
                    RotationManager.getRotationsToPosition(selected.boundingBox.center), 250
                )
            }
            BlockInteractionUtils.attackEntity(selected)
        }
    }

    private fun placeItem(item: Item, hit: BlockHitResult?, forcedRotations: FloatArray?): Boolean {
        if (hit == null) {
            return false
        }
        var alternative = this.alternativeSetting.value
        if (antiCheat.is2b2tEnabled && InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 2)) {
            alternative = false
        }
        val previousSlot = InventoryUtils.getSelectedSlot()
        val slot = InventoryUtils.findSwapSlot(item, alternative)
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        if (!slot.isFound || (!slot.hotbar && slot.hand == InteractionHand.MAIN_HAND && player.containerMenu != player.inventoryMenu)) {
            return false
        }
        if (this.rotateSetting.value) {
            ClientContext.rotationManager.requestRotation(RotationManager.getRotationsToPosition(hit.location), 250)
        } else if (forcedRotations != null) {
            ClientContext.rotationManager.requestRotation(forcedRotations, 250)
        }
        val interaction = Runnable {
            PacketSender.sendUseItemOn(slot.hand, hit)
            ClientContext.swingManager.swing(slot.hand, SwingAction.INTERACT, true)
        }
        InventoryUtils.runWithSwap(previousSlot, slot) {
            if (BlockInteractionUtils.requiresSneakForInteraction(hit.blockPos)) {
                ClientContext.playerStateTracker.runSneaking(interaction)
            } else {
                interaction.run()
            }
        }
        return true
    }

    private fun isBlocked(currentPlan: PistonCrystalPlan): Boolean {
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val piston = level.getBlockState(currentPlan.pistonPosition)
        var blocked = false
        this.forceCrystal = false
        if (piston.block !is PistonBaseBlock) {
            blocked = this.hasBlockingEntities(AABB(currentPlan.pistonPosition)) { entity -> !entity.isSpectator && entity !is ItemEntity }
        } else if (piston.getValue(DirectionalBlock.FACING) != currentPlan.pistonFacing
            || (piston.getValue(PistonBaseBlock.EXTENDED) && this.cycleTimer.hasElapsed(300L) && this.powerPlaced)) {
            this.forceCrystal = true
        }
        if (this.hasBlockingEntities(AABB(currentPlan.crystalPosition)) { this.isOldEntity(it) } || (this.crystalPlaced && this.powerPlaced)) {
            blocked = true
        }
        return blocked || this.forceCrystal
    }

    private fun isOldEntity(entity: Entity): Boolean {
        return !entity.isSpectator && entity.tickCount >= 10
    }

    private fun resetStages() {
        this.pistonPlaced = false
        this.crystalPlaced = false
        this.powerPlaced = false
    }

    private fun selectPowerItem() {
        if (InventoryUtils.findSwapSlot(Items.REDSTONE_BLOCK, this.alternativeSetting.value).isFound) {
            this.powerItem = Items.REDSTONE_BLOCK
        } else if (InventoryUtils.findSwapSlot(Items.REDSTONE_TORCH, this.alternativeSetting.value).isFound) {
            this.powerItem = Items.REDSTONE_TORCH
        } else {
            this.powerItem = Items.AIR
        }
    }

    fun getPowerItem(): Item {
        return this.powerItem
    }

    fun hasBlockingEntities(bounds: AABB, predicate: Predicate<Entity>): Boolean {
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        for (entity in level.entitiesForRendering()) {
            if (predicate.test(entity) && entity.boundingBox.intersects(bounds)) {
                return true
            }
        }
        return false
    }

    fun crystalBounds(position: BlockPos): AABB {
        return AABB(position).expandTowards(0.0, 1.0, 0.0)
    }

    companion object {
        @JvmStatic
        lateinit var antiCheat: AntiCheatModule

        @JvmStatic
        lateinit var multiTask: MultiTaskModule

        @JvmStatic
        fun bindModules(modules: ModuleRegistry) {
            antiCheat = modules.getModule(AntiCheatModule::class.java) as AntiCheatModule
            multiTask = modules.getModule(MultiTaskModule::class.java) as MultiTaskModule
        }
    }
}
