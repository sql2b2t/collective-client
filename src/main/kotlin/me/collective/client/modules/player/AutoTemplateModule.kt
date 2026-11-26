package me.collective.client.modules.player

import net.minecraft.world.item.Item

import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.NamedEntry
import me.collective.client.utils.ElapsedTimer
import java.util.Comparator
import java.util.Set
import java.util.function.Predicate
import net.minecraft.tags.ItemTags
import net.minecraft.client.gui.screens.inventory.SmithingScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Repairable


class AutoTemplateModule : Module("AutoTemplate", "Automatically applies selected smithing templates.", ModuleCategory.PLAYER, "AutoUpgrade") {
   private val templatesSetting: RegistrySetSetting<Item>
   private val materialsSetting: RegistrySetSetting<Item>
   private val itemsSetting: Setting<ItemsChoice>
   private val delaySetting: NumberSetting<Int>
   private val safeSetting: Setting<Boolean>
   private val actionTimer = ElapsedTimer()

   init {
      this.templatesSetting = this.add(RegistrySetSetting("Templates", BuiltInRegistries.ITEM))
      this.materialsSetting = this.add(RegistrySetSetting("Materials", BuiltInRegistries.ITEM))
      this.itemsSetting = this.add(EnumSetting("Items", ItemsChoice.ANY))
      this.delaySetting = this.add(NumberSetting("Delay", 0, 0, 20))
      this.safeSetting = this.add(BooleanSetting("Safe", true))
   }

   @EventHandler
   private fun onPlayerTick(event: LocalPlayerPreTickEvent) {
      if (this.isWorldUnavailable() || ClientAccess.collectiveMinecraft.gui.screen() !is SmithingScreen) return
      val menu = ClientAccess.collectiveMinecraft.player!!.containerMenu
      if (!this.actionTimer.hasElapsed(this.delaySetting.value!!.toLong() * 50L)) return
      if (!menu.getSlot(3).item.isEmpty) {
         this.quickMove(menu, 3)
         return
      }
      val template = this.findTemplate(menu) ?: return
      val templatePredicate = Predicate<ItemStack> { stack -> stack.`is`(template) }
      val basePredicate = this.getBasePredicate(template)
      val materialPredicate = this.getMaterialPredicate(template)
      val materialSlot = this.findBestMaterialSlot(menu, materialPredicate)
      val preferredMaterial = materialPredicate.and(Predicate { stack ->
         materialSlot == -1 ||
         MATERIAL_ORDER.compare(menu.getSlot(materialSlot).item, stack) >= 0
      })
      if (this.clearInvalidInput(menu, 0, templatePredicate) ||
         this.clearInvalidInput(menu, 1, basePredicate) ||
         this.clearInvalidInput(menu, 2, preferredMaterial)
      ) return
      if (this.fillInput(menu, 0, this.findSourceSlot(menu, templatePredicate))) return
      if (this.fillInput(menu, 1, this.findSourceSlot(menu, basePredicate))) return
      this.fillInput(menu, 2, materialSlot)
   }

   private fun findTemplate(menu: AbstractContainerMenu): Item? {
      val reserve = if (this.safeSetting.value!!) 1 else 0
      for (template in this.templatesSetting.value!!) {
         var count = 0
         for (slot in menu.slots) {
            val stack = slot.item
            if (stack.`is`(template)) count += stack.count
         }
         if (count > reserve && this.hasInput(menu, 1, this.getBasePredicate(template)) &&
         this.hasInput(menu, 2, this.getMaterialPredicate(template))
         ) return template
      }
      return null
   }

   private fun getBasePredicate(template: Item): Predicate<ItemStack> {
      return if (template === Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) Predicate { stack -> this.isEligibleItem(stack) }
      else Predicate { stack -> stack.`is`(ItemTags.TRIMMABLE_ARMOR) && !stack.has(DataComponents.TRIM) }
   }

   private fun getMaterialPredicate(template: Item): Predicate<ItemStack> {
      return if (template === Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) Predicate { stack -> stack.`is`(Items.NETHERITE_INGOT) }
      else Predicate { stack -> this.materialsSetting.value!!.contains(stack.item) }
   }

   private fun hasInput(menu: AbstractContainerMenu, slot: Int, predicate: Predicate<ItemStack>): Boolean {
      return predicate.test(menu.getSlot(slot).item) || this.findSourceSlot(menu, predicate) != -1
   }

   private fun clearInvalidInput(menu: AbstractContainerMenu, slot: Int, predicate: Predicate<ItemStack>): Boolean {
      val stack = menu.getSlot(slot).item
      if (stack.isEmpty || predicate.test(stack)) return false
      this.quickMove(menu, slot)
      return true
   }

   private fun fillInput(menu: AbstractContainerMenu, target: Int, source: Int): Boolean {
      if (source == -1 || !menu.getSlot(target).item.isEmpty) return false
      InventoryUtils.swapSlotsWithPickup(source, target)
      this.actionTimer.reset()
      return true
   }

   private fun findSourceSlot(menu: AbstractContainerMenu, predicate: Predicate<ItemStack>): Int {
      for (slot in 4 until 40) {
         val stack = menu.getSlot(slot).item
         if (!stack.isEmpty && predicate.test(stack)) return slot
      }
      return -1
   }

   private fun findBestMaterialSlot(menu: AbstractContainerMenu, predicate: Predicate<ItemStack>): Int {
      var best = -1
      for (slot in 4 until 40) {
         val stack = menu.getSlot(slot).item
         if (!stack.isEmpty && predicate.test(stack) &&
         (best == -1 || MATERIAL_ORDER.compare(stack, menu.getSlot(best).item) < 0)
         ) best = slot
      }
      return best
   }

   private fun quickMove(menu: AbstractContainerMenu, slot: Int) {
      InventoryUtils.clickSlot(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE)
      this.actionTimer.reset()
   }

   private fun isEligibleItem(stack: ItemStack): Boolean {
      val repairable = stack.get(DataComponents.REPAIRABLE)
      if (repairable !is Repairable ||
         !repairable.isValidRepairItem(DIAMOND_REPAIR_ITEM)
      ) return false
      val choice = this.itemsSetting.value!!
      return choice != ItemsChoice.TOOLS && stack.has(DataComponents.EQUIPPABLE) ||
         choice != ItemsChoice.ARMOR && stack.has(DataComponents.TOOL)
   }

   enum class ItemsChoice(private val displayName: String) : NamedEntry {
      ANY("Any"), TOOLS("Tools"), ARMOR("Armor");

      override fun getName(): String {
         return this.displayName
      }
   }

   companion object {
      private val DIAMOND_REPAIR_ITEM by lazy { ItemStack(Items.DIAMOND) }
      private val MATERIAL_ORDER: Comparator<ItemStack> = Comparator.comparing { stack: ItemStack -> stack.`is`(Items.NETHERITE_INGOT) }
   }
}
