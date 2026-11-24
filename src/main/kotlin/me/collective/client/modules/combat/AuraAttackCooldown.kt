package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.entity.PlayerState
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.InventoryUtils
import net.minecraft.core.component.DataComponents
import net.minecraft.util.Mth
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemAttributeModifiers

class AuraAttackCooldown(private val aura: AuraModule) : ClientAccess {
    fun getAttackStrength(stack: ItemStack): Float {
        var partialTick = MathConstants.ONE_HALF.toFloat()

        if (aura.tPSSyncSetting.value) {
            val tickManager = ClientContext.serverStateManager
            partialTick -= 20.0f - tickManager.tickRate
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return 0.0f
        val ticksSinceAttack = (player as PlayerState).`collective$getLastAttackedTicksServer`()
        val attackSpeed = player.getAttributeBaseValue(Attributes.ATTACK_SPEED)
        var effectiveSpeed = attackSpeed
        val modifiers: ItemAttributeModifiers? = stack.get(DataComponents.ATTRIBUTE_MODIFIERS)

        if (stack.`is`(Items.MACE) && PlayerStateUtils.getTrackedFallDistance() > 1.0f) {
            effectiveSpeed = attackSpeed
        } else if (modifiers != null) {
            effectiveSpeed = ItemAttributeUtils.applyModifiers(Attributes.ATTACK_SPEED, attackSpeed, modifiers.modifiers())
        }

        val attackPeriod = 1.0 / effectiveSpeed
        val attackTicks = (attackPeriod * 20.0).toFloat()
        val strength = (ticksSinceAttack + partialTick) / attackTicks
        return Mth.clamp(strength, 0.0f, 1.0f)
    }

    fun getSlotAttackStrength(slot: InventoryUtils.SwapSlot): Float {
        val stack = if (!slot.isFound()) {
            ClientAccess.collectiveMinecraft.player?.mainHandItem ?: ItemStack.EMPTY
        } else {
            slot.getStack()
        }
        return getAttackStrength(stack)
    }
}
