package me.collective.client.utils.inventory

import me.collective.client.ClientAccess

import it.unimi.dsi.fastutil.objects.Object2IntMap
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.ItemEnchantments

class EnchantmentUtils private constructor() : ClientAccess {
   init {
      throw IllegalArgumentException("Illegal constructor call")
   }

   companion object {
      @JvmStatic
      fun getLevel(enchantment: ResourceKey<Enchantment>, stack: ItemStack): Int {
         if (stack.isEmpty) {
            return 0
         }

         for (entry in getEntries(stack)) {
            if (entry.key.`is`(enchantment)) {
               return entry.intValue
            }
         }

         return 0
      }

      @JvmStatic
      fun getLevel(
         enchantment: ResourceKey<Enchantment>, slot: EquipmentSlot, entity: LivingEntity
      ): Int {
         val stack = entity.getItemBySlot(slot)
         return getLevel(enchantment, stack)
      }

      @JvmStatic
      fun getLevel(enchantment: ResourceKey<Enchantment>, slot: EquipmentSlot): Int {
         val player = ClientAccess.collectiveMinecraft.player ?: return 0
         return getLevel(enchantment, slot, player)
      }

      @JvmStatic
      fun hasEnchantment(enchantment: ResourceKey<Enchantment>, stack: ItemStack): Boolean {
         return getLevel(enchantment, stack) > 0
      }

      @JvmStatic
      fun hasEnchantment(
         enchantment: ResourceKey<Enchantment>, slot: EquipmentSlot, entity: LivingEntity
      ): Boolean {
         return getLevel(enchantment, slot, entity) > 0
      }

      @JvmStatic
      fun hasEnchantment(enchantment: ResourceKey<Enchantment>, slot: EquipmentSlot): Boolean {
         return getLevel(enchantment, slot) > 0
      }

      @JvmStatic
      fun getLevels(stack: ItemStack): Map<ResourceKey<Enchantment>, Int> {
         if (stack.isEmpty) {
            return emptyMap()
         }

         val levels = HashMap<ResourceKey<Enchantment>, Int>()
         for (entry in getEntries(stack)) {
            val key = entry.key.unwrapKey()
            if (key.isPresent) {
               val enchantmentKey = key.get()
               val level = entry.intValue
               levels[enchantmentKey] = level
            }
         }

         return levels
      }

      private fun getEntries(stack: ItemStack): Set<Object2IntMap.Entry<Holder<Enchantment>>> {
         val enchantedBook = Items.ENCHANTED_BOOK
         return if (stack.`is`(enchantedBook) && stack.has(DataComponents.STORED_ENCHANTMENTS)) {
            (stack.get(DataComponents.STORED_ENCHANTMENTS) as ItemEnchantments).entrySet()
         } else {
            stack.getEnchantments().entrySet()
         }
      }

      @JvmStatic
      fun getHolder(enchantment: ResourceKey<Enchantment>): Holder<Enchantment>? {
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return null
         return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
            .get(enchantment.location()).orElse(null)
      }

      @JvmStatic
      fun getHolder(name: String): Holder<Enchantment>? {
         val key = getKey(name)
         return getHolder(key)
      }

      @JvmStatic
      fun getKey(name: String): ResourceKey<Enchantment> {
         val registryKey = Registries.ENCHANTMENT
         val identifier = ResourceLocation.withDefaultNamespace(name)
         return ResourceKey.create(registryKey, identifier)
      }
   }
}
