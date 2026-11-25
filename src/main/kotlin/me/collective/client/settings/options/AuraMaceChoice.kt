package me.collective.client.settings.options

import me.collective.client.ClientContext
import me.collective.client.utils.inventory.EnchantmentUtils
import me.collective.client.utils.entity.PlayerStateTracker
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.settings.NamedEntry
import java.util.function.Predicate
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments

enum class AuraMaceChoice(private val displayName: String, private val predicate: Predicate<ItemStack>) : NamedEntry {
    NONE("None", Predicate { _ -> false }),
    BREACH("Breach", Predicate { stack: ItemStack ->
        val enchantment: ResourceKey<Enchantment> = Enchantments.BREACH
        EnchantmentUtils.hasEnchantment(enchantment, stack)
    }),
    DENSITY("Density", Predicate { stack: ItemStack ->
        val enchantment: ResourceKey<Enchantment> = Enchantments.DENSITY
        EnchantmentUtils.hasEnchantment(enchantment, stack) && PlayerStateUtils.getTrackedFallDistance() > 3.0f
    }),
    SMART("Smart", Predicate { stack: ItemStack ->
        if (!(PlayerStateUtils.getTrackedFallDistance() > 3.0f)) {
            val playerState: PlayerStateTracker = ClientContext.playerStateTracker
            if (playerState.getGroundedTicks() > 1) {
                val enchantment: ResourceKey<Enchantment> = Enchantments.BREACH
                return@Predicate EnchantmentUtils.hasEnchantment(enchantment, stack)
            }
        }
        val enchantment: ResourceKey<Enchantment> = Enchantments.DENSITY
        EnchantmentUtils.hasEnchantment(enchantment, stack)
    });

    override fun getName(): String = displayName

    fun matches(stack: ItemStack): Boolean = predicate.test(stack)
}
