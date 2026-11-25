package me.collective.client.managers

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.modules.combat.AutoCrystalModule
import me.collective.client.modules.combat.ItemAttributeUtils
import me.collective.client.modules.misc.SwingModule
import me.collective.client.settings.options.AutoCrystalInstantChoice
import me.collective.client.settings.options.AutoCrystalWeaknessChoice
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.entity.PlayerState
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.network.PacketSender
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class CrystalInteractionManager : ClientAccess {
    private val attackCooldownTimer = ElapsedTimer()
    private var additionalCooldownMillis: Long = 0L
    private var lastAttackedEntityId: Int = 0

    init {
        ClientAccess.eventBus.subscribe(this)
    }

    val minimumCrystalAge: Int
        @JvmName("getMinimumCrystalAgeProp")
        get() = autoCrystal().ticksExistedSetting.value

    fun getMinimumCrystalAge(): Int = minimumCrystalAge

    fun setLastAttackedEntityId(entityId: Int) {
        this.lastAttackedEntityId = entityId
    }

    val isRotationEnabled: Boolean
        @JvmName("isRotationEnabledProp")
        get() = autoCrystal().rotateSetting.value

    fun isRotationEnabled(): Boolean = isRotationEnabled

    val isInstantAttackEnabled: Boolean
        @JvmName("isInstantAttackEnabledProp")
        get() = autoCrystal().instantSetting.value != AutoCrystalInstantChoice.NONE

    fun isInstantAttackEnabled(): Boolean = isInstantAttackEnabled

    val attackDelayMillis: Int
        @JvmName("getAttackDelayMillisProp")
        get() = autoCrystal().breakDelaySetting.value

    fun getAttackDelayMillis(): Int = attackDelayMillis

    private fun selectWeaknessWeapon(): Boolean {
        if (autoCrystal().weaknessSetting.value == AutoCrystalWeaknessChoice.NONE) {
            return false
        }
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val weaknessInstance = player.getEffect(MobEffects.WEAKNESS) ?: return false

        val weaknessReduction = 4.0 * (weaknessInstance.amplifier + 1)
        if (getItemAttackDamage(player.mainHandItem) > weaknessReduction) {
            return false
        }

        var weaponSlot = InventoryUtils.findHotbarSlot { candidate ->
            (candidate.item is net.minecraft.world.item.SwordItem || candidate.item is net.minecraft.world.item.DiggerItem) && getItemAttackDamage(candidate) > weaknessReduction
        }
        if (weaponSlot == -1) {
            weaponSlot = InventoryUtils.findHotbarSlot { candidate ->
                getItemAttackDamage(candidate) > weaknessReduction
            }
        }
        if (weaponSlot == -1) {
            return false
        }

        if (autoCrystal().weaknessSetting.value == AutoCrystalWeaknessChoice.SILENT) {
            autoCrystal().ignoreNextSlotChange()
        }

        InventoryUtils.selectHotbarSlotForPlacement(weaponSlot)
        return true
    }

    private fun scheduleClientAttack(entityId: Int) {
        if (!RotationManager.isMovementSyncEnabled() || RotationManager.is2b2tMode()) {
            return
        }
        ClientAccess.collectiveMinecraft.execute {
            val level = ClientAccess.collectiveMinecraft.level ?: return@execute
            val player = ClientAccess.collectiveMinecraft.player ?: return@execute
            val targetEntity = level.getEntity(entityId) ?: return@execute
            player.attack(targetEntity)
        }
    }

    private fun resetAttackCooldown(hand: InteractionHand) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        (player as PlayerState).`collective$resetLastAttackedServer`()

        val elapsedThreshold = 200L + additionalCooldownMillis
        if (!attackCooldownTimer.hasElapsedAndReset(elapsedThreshold) || hand != InteractionHand.MAIN_HAND) {
            return
        }
        val swingModule = swing()
        if (swingModule.handSetting.value == SwingModule.HandChoice.OFFHAND) {
            return
        }
        if (swingModule.typeSetting.value == SwingModule.TypeChoice.VANILLA) {
            return
        }

        player.resetAttackStrengthTicker()
        additionalCooldownMillis = (Math.random() * 300.0).toLong()
    }

    @JvmOverloads
    fun attackEntity(entityId: Int, instantAttack: Boolean = false): Boolean {
        if (!isInstantAttackEnabled() && instantAttack) {
            return false
        }

        if (lastAttackedEntityId != -1 && RotationManager.isMovementSyncEnabled()) {
            return false
        }

        val previousSlot = InventoryUtils.getSelectedSlot()
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val targetEntity = level.getEntity(entityId)

        var switchedForWeakness = false
        if (targetEntity is EndCrystal || instantAttack) {
            switchedForWeakness = selectWeaknessWeapon()
        }

        lastAttackedEntityId = entityId

        val hand = selectAttackHand(targetEntity, instantAttack)

        PacketSender.sendAttack(lastAttackedEntityId)
        if (!instantAttack) {
            ClientContext.swingManager.swingClientSide(hand)
        }

        ClientContext.swingManager.swingPacket(InteractionHand.MAIN_HAND, SwingAction.HIT)

        resetAttackCooldown(hand)
        scheduleClientAttack(entityId)

        if (switchedForWeakness && autoCrystal().weaknessSetting.value == AutoCrystalWeaknessChoice.SILENT) {
            autoCrystal().ignoreNextSlotChange()
            InventoryUtils.selectHotbarSlotForPlacement(previousSlot)
        }

        return true
    }

    private fun selectAttackHand(entity: Entity?, instantAttack: Boolean): InteractionHand {
        if (entity is EndCrystal && !instantAttack) {
            val player = ClientAccess.collectiveMinecraft.player
            if (player != null && player.offhandItem.item == Items.END_CRYSTAL) {
                return InteractionHand.OFF_HAND
            }
        }
        return InteractionHand.MAIN_HAND
    }

    @EventHandler(priority = -300)
    private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
        lastAttackedEntityId = -1
    }

    companion object {
        private var swing: SwingModule? = null
        private var autoCrystal: AutoCrystalModule? = null

        private fun swing(): SwingModule {
            return swing ?: (ClientContext.getModule(SwingModule::class.java) as SwingModule).also { swing = it }
        }

        private fun autoCrystal(): AutoCrystalModule {
            return autoCrystal ?: (ClientContext.getModule(AutoCrystalModule::class.java) as AutoCrystalModule).also { autoCrystal = it }
        }

        private fun getItemAttackDamage(stack: ItemStack): Double {
            if (stack.isEmpty) {
                return 1.0
            }
            val modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS) ?: return 1.0
            return ItemAttributeUtils.applyModifiers(Attributes.ATTACK_DAMAGE, 1.0, modifiers)
        }
    }
}
