package me.collective.client.modules.combat

import net.minecraft.core.Holder
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.component.ItemAttributeModifiers.Entry

object ItemAttributeUtils {
    @JvmStatic
    fun findModifier(attribute: Holder<Attribute>, modifiers: ItemAttributeModifiers): Entry? {
        return findModifier(attribute, modifiers.modifiers())
    }

    @JvmStatic
    fun findModifier(attribute: Holder<Attribute>, modifiers: List<Entry>): Entry? {
        for (entry in modifiers) {
            if (entry.attribute() == attribute) {
                return entry
            }
        }
        return null
    }

    @JvmStatic
    fun applyModifiers(attribute: Holder<Attribute>, baseValue: Double, modifiers: ItemAttributeModifiers): Double {
        return applyModifiers(attribute, baseValue, modifiers.modifiers())
    }

    @JvmStatic
    fun applyModifiers(attribute: Holder<Attribute>, baseValue: Double, modifiers: List<Entry>): Double {
        var value = baseValue
        for (entry in modifiers) {
            if (entry.attribute() == attribute) {
                value = applyModifier(baseValue, value, entry.modifier())
            }
        }
        return value
    }

    private fun applyModifier(baseValue: Double, value: Double, modifier: AttributeModifier): Double {
        val amount = when (modifier.operation()) {
            Operation.ADD_VALUE -> modifier.amount()
            Operation.ADD_MULTIPLIED_BASE -> modifier.amount() * baseValue
            Operation.ADD_MULTIPLIED_TOTAL -> modifier.amount() * value
        }
        return value + amount
    }
}
