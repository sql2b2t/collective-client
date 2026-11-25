package me.collective.client.utils.inventory

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.SmithingTemplateItem
import net.minecraft.world.item.component.Fireworks
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ShulkerBoxBlock
import net.minecraft.world.level.block.StainedGlassBlock
import net.minecraft.world.level.block.StainedGlassPaneBlock

class ItemUtils private constructor() {
   companion object {
      private val maceDisplayName: Component = createCompatibilityName("1.21 Mace")
      private val spearDisplayNames: Set<Component> = setOf(
         createCompatibilityName("1.21.11 Wooden Spear"),
         createCompatibilityName("1.21.11 Stone Spear"),
         createCompatibilityName("1.21.11 Golden Spear"),
         createCompatibilityName("1.21.11 Copper Spear"),
         createCompatibilityName("1.21.11 Diamond Spear"),
         createCompatibilityName("1.21.11 Netherite Spear")
      )

      @JvmStatic
      fun isSpear(stack: ItemStack): Boolean {
         val nameComponent: DataComponentType<Component> = DataComponents.CUSTOM_NAME
         val customName = stack.get(nameComponent)
         return customName != null && spearDisplayNames.contains(customName)
      }

      @JvmStatic
      fun isGlass(item: Item): Boolean {
         if (item !is BlockItem) {
            return false
         }

         return item.block is StainedGlassBlock ||
         item.block === Blocks.TINTED_GLASS ||
         item.block === Blocks.GLASS ||
         item.block is StainedGlassPaneBlock ||
         item.block === Blocks.GLASS_PANE
      }

      @JvmStatic
      fun isMace(stack: ItemStack): Boolean {
         val nameComponent: DataComponentType<Component> = DataComponents.CUSTOM_NAME
         val customName = stack.get(nameComponent)
         if (maceDisplayName == customName) {
            return true
         }

         val mace = Items.MACE
         return stack.`is`(mace)
      }

      @JvmStatic
      fun isShulkerBox(item: Item): Boolean {
         if (item is BlockItem) {
            return item.block is ShulkerBoxBlock
         }

         return false
      }

      @JvmStatic
      fun isSword(stack: ItemStack): Boolean {
         val tag = ItemTags.SWORDS
         return stack.`is`(tag)
      }

      @JvmStatic
      fun isPickaxe(stack: ItemStack): Boolean {
         val tag = ItemTags.PICKAXES
         return stack.`is`(tag)
      }

      @JvmStatic
      fun isAxe(stack: ItemStack): Boolean {
         val tag = ItemTags.AXES
         return stack.`is`(tag)
      }

      @JvmStatic
      fun isSmithingTemplate(item: Item): Boolean {
         return item is SmithingTemplateItem
      }

      @JvmStatic
      @Suppress("DEPRECATION")
      fun providesTrimMaterial(item: Item): Boolean {
         return try {
            item.builtInRegistryHolder().`is`(ItemTags.TRIM_MATERIALS)
         } catch (_: IllegalStateException) {
            item == Items.QUARTZ || item == Items.IRON_INGOT || item == Items.NETHERITE_INGOT ||
               item == Items.REDSTONE || item == Items.COPPER_INGOT || item == Items.GOLD_INGOT ||
               item == Items.EMERALD || item == Items.DIAMOND || item == Items.LAPIS_LAZULI ||
               item == Items.AMETHYST_SHARD || item == Items.RESIN_BRICK
         }
      }

      @JvmStatic
      fun isFood(item: Item): Boolean {
         val foodComponents: DataComponentMap = item.components()
         val foodType: DataComponentType<*> = DataComponents.FOOD
         if (foodComponents.has(foodType)) {
            val consumableComponents: DataComponentMap = item.components()
            val consumableType: DataComponentType<*> = DataComponents.CONSUMABLE
            if (consumableComponents.has(consumableType)) {
               return true
            }
         }

         return false
      }

      @JvmStatic
      fun isFood(stack: ItemStack): Boolean {
         val item = stack.item
         return isFood(item)
      }

      @JvmStatic
      fun getFireworkDuration(stack: ItemStack): Int {
         val fireworksComponent: DataComponentType<Fireworks> = DataComponents.FIREWORKS
         val fireworks = stack.get(fireworksComponent)
         if (fireworks != null) {
            val flightDuration = 1 + fireworks.flightDuration()

            return flightDuration * (10) + 1
         } else {
            return 1
         }
      }

      private fun createCompatibilityName(name: String): Component {
         val literal: MutableComponent = Component.literal(name)
         val nonItalicStyle: Style = Style.EMPTY.withItalic(false)
         val color = ChatFormatting.WHITE
         return literal.setStyle(nonItalicStyle.withColor(color))
      }
   }
}
