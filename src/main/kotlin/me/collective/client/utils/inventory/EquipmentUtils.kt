package me.collective.client.utils.inventory

import java.util.Optional
import net.minecraft.core.HolderSet
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.item.enchantment.Repairable
import net.minecraft.world.item.equipment.Equippable

class EquipmentUtils private constructor() {
   companion object {
      private val REPAIR_MATERIALS_BY_TAG: Map<String, Item> = java.util.Map.of(
         "minecraft:repairs_chain_armor", Items.IRON_INGOT,
         "minecraft:repairs_diamond_armor", Items.DIAMOND,
         "minecraft:repairs_gold_armor", Items.GOLD_INGOT,
         "minecraft:repairs_iron_armor", Items.IRON_INGOT,
         "minecraft:repairs_leather_armor", Items.LEATHER,
         "minecraft:repairs_netherite_armor", Items.NETHERITE_INGOT,
         "minecraft:repairs_turtle_armor", Items.TURTLE_SCUTE,
         "minecraft:repairs_wolf_armor", Items.ARMADILLO_SCUTE
      )
      private val HELMET_ENCHANTMENTS: List<EnchantmentRequirement> = listOf(
         EnchantmentRequirement(Enchantments.PROTECTION, 4),
         EnchantmentRequirement(Enchantments.UNBREAKING, 3),
         EnchantmentRequirement(Enchantments.MENDING, 1)
      )
      private val CHESTPLATE_ENCHANTMENTS: List<EnchantmentRequirement> = listOf(
         EnchantmentRequirement(Enchantments.PROTECTION, 4),
         EnchantmentRequirement(Enchantments.UNBREAKING, 3),
         EnchantmentRequirement(Enchantments.MENDING, 1)
      )
      private val LEGGINGS_ENCHANTMENTS: List<EnchantmentRequirement> = listOf(
         EnchantmentRequirement(Enchantments.BLAST_PROTECTION, 4),
         EnchantmentRequirement(Enchantments.UNBREAKING, 3),
         EnchantmentRequirement(Enchantments.MENDING, 1)
      )
      private val BOOTS_ENCHANTMENTS: List<EnchantmentRequirement> = listOf(
         EnchantmentRequirement(Enchantments.PROTECTION, 4),
         EnchantmentRequirement(Enchantments.UNBREAKING, 3),
         EnchantmentRequirement(Enchantments.MENDING, 1),
         EnchantmentRequirement(Enchantments.FEATHER_FALLING, 4)
      )
      private val ELYTRA_ENCHANTMENTS: List<EnchantmentRequirement> = listOf(
         EnchantmentRequirement(Enchantments.UNBREAKING, 3),
         EnchantmentRequirement(Enchantments.MENDING, 1)
      )
      private val WEAPON_ENCHANTMENTS: List<EnchantmentRequirement> = listOf(
         EnchantmentRequirement(Enchantments.SHARPNESS, 5),
         EnchantmentRequirement(Enchantments.FIRE_ASPECT, 2),
         EnchantmentRequirement(Enchantments.KNOCKBACK, 2),
         EnchantmentRequirement(Enchantments.SWEEPING_EDGE, 3),
         EnchantmentRequirement(Enchantments.UNBREAKING, 3),
         EnchantmentRequirement(Enchantments.MENDING, 1)
      )

      @JvmStatic
      fun hasRequiredEnchantments(stack: ItemStack): Boolean {
         val equippableType: DataComponentType<*> = DataComponents.EQUIPPABLE
         val equippable = stack.get(equippableType)
         if (equippable is Equippable) {
            val slot: EquipmentSlot = equippable.slot()
            return when (slot) {
               EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.SADDLE, EquipmentSlot.BODY -> false
               EquipmentSlot.FEET -> matchesEnchantments(stack, BOOTS_ENCHANTMENTS)
               EquipmentSlot.LEGS -> matchesEnchantments(stack, LEGGINGS_ENCHANTMENTS)
               EquipmentSlot.CHEST -> matchesEnchantments(stack, CHESTPLATE_ENCHANTMENTS)
               EquipmentSlot.HEAD -> matchesEnchantments(stack, HELMET_ENCHANTMENTS)
            }
         } else {
            if (stack.has(DataComponents.WEAPON)) {
               return matchesEnchantments(stack, WEAPON_ENCHANTMENTS)
            }

            val elytra = Items.ELYTRA
            return stack.`is`(elytra) && matchesEnchantments(stack, ELYTRA_ENCHANTMENTS)
         }
      }

      @JvmStatic
      fun getArmorStacks(player: Player): List<ItemStack> {
         val armorStacks = ArrayList<ItemStack>()

         for (slot in EquipmentSlot.entries) {
            if (slot.type == EquipmentSlot.Type.HUMANOID_ARMOR) {
               val inventory = player.inventory

               val inventoryIndex = slot.getIndex(36)
               armorStacks.add(inventory.getItem(inventoryIndex))
            }
         }

         return armorStacks
      }

      private fun matchesEnchantments(stack: ItemStack, requirements: Iterable<EnchantmentRequirement>): Boolean {
         for (requirement in requirements) {
            val enchantment: ResourceKey<Enchantment> = requirement.enchantment
            val actualLevel = EnchantmentUtils.getLevel(enchantment, stack)
            if (actualLevel != requirement.level) {
               return false
            }
         }

         return true
      }

      @JvmStatic
      fun isRepairMaterial(stack: ItemStack, material: Item): Boolean {
         val repairableType: DataComponentType<Repairable> = DataComponents.REPAIRABLE
         val repairable = stack.get(repairableType)
         if (repairable == null) {
            return false
         }

         val repairItems: HolderSet<Item> = repairable.items()
         val tagKey: Optional<TagKey<Item>> = repairItems.unwrapKey()
         val repairTag: TagKey<Item>? = tagKey.orElse(null)
         if (repairTag == null) {
            return false
         }

         val materialsByTag: Map<String, Item> = REPAIR_MATERIALS_BY_TAG
         val tagLocation: Identifier = repairTag.location()
         val repairMaterial = materialsByTag[tagLocation.toString()]
         return repairMaterial != null && repairMaterial === material
      }
   }

   private data class EnchantmentRequirement(
      val enchantment: ResourceKey<Enchantment>,
      val level: Int
   )
}
