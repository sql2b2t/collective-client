package me.collective.client.modules.player

import java.util.Set

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.gui.screens.ShulkerPreviewScreen
import me.collective.client.managers.InventoryStateManager
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.ListFilterMode

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen
import net.minecraft.core.DefaultedRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

class InventoryCleanerModule : Module("InventoryCleaner", "Gets rid of the selected items in your inventory.", ModuleCategory.PLAYER) {
   private val itemsSetting: RegistrySetSetting<Item>
   private val selectionSetting: Setting<ListFilterMode>
   private val delaySetting: Setting<Int>
   private val frequencySetting: Setting<Int>
   private val offhandSetting: Setting<Boolean>
   private val containersSetting: Setting<Boolean>
   private val onlyUISetting: Setting<Boolean>
   private val ignoreHotbarSetting: Setting<Boolean>
   private val cleaningTimer: ElapsedTimer

   @EventHandler(priority = 100)
   private fun discardSelectedStacks(event: LocalPlayerTickEvent) {
      var discardAttempts = 0
      val capturedTimer = this.cleaningTimer
      val delayMillis = this.delaySetting.value!!.toLong()
      if (!capturedTimer.hasElapsed(delayMillis)) {
         return
      }
      val cleanContainers = this.containersSetting.value!!
      if (!cleanContainers &&
         ClientAccess.collectiveMinecraft.player!!.inventoryMenu !==
         ClientAccess.collectiveMinecraft.player!!.containerMenu
      ) {
         return
      }
      val screen: Screen? = ClientAccess.collectiveMinecraft.screen
      val supportedScreen = screen is InventoryScreen || screen is ContainerScreen ||
         (screen is ShulkerBoxScreen && screen !is ShulkerPreviewScreen)
      if (this.onlyUISetting.value!! && !supportedScreen) {
         return
      }
      val playerInventoryMenu: Boolean
      var slotCountForHotbar = ClientAccess.collectiveMinecraft.player!!.containerMenu.slots.size
      if (ClientAccess.collectiveMinecraft.player!!.containerMenu is InventoryMenu) {
         slotCountForHotbar--
         playerInventoryMenu = true
      } else {
         playerInventoryMenu = false
      }

      val slots = ClientAccess.collectiveMinecraft.player!!.containerMenu.slots.iterator()

      while (slots.hasNext()) {
         val slot = slots.next()

         val stack = slot.item
         val capturedClickManager = ClientContext.inventoryStateManager

         val clickGateKind = 1

         if (InventoryStateManager.wouldReachClickLimit(capturedClickManager, clickGateKind)) {
            break
         }

         if (stack.isEmpty) {
            continue
         }
         val selectedPolicy = this.selectionSetting.value!!

         val selectedItem = stack.item
         val itemsOption = this.itemsSetting

         if (!ListFilterMode.matches(selectedPolicy, selectedItem, itemsOption)) {
            continue
         }

         if (slot.index >= slotCountForHotbar - (9)) {
            if (slot.index != (45)) {
               if (this.ignoreHotbarSetting.value!!) {
                  continue
               }
            }
         }

         // Слот 45 — offhand только в InventoryMenu: в ChestMenu/ShulkerBoxMenu
         // это обычный слот контейнера/инвентаря, и правило offhand неприменимо.
         if (playerInventoryMenu && slot.index == (45)) {
            val cleanOffhand = this.offhandSetting.value!!
            if (!cleanOffhand) {
               continue
            }
         }

         if (playerInventoryMenu) {
            if (slot.index >= (5)) {
               if (slot.index <= (8)) {
                  continue
               }
            }

            if (slot.index == 0) {
               continue
            }
         }

         val clickType = ClickType.THROW
         val throwButton = 1
         val throwSlot = slot.index
         InventoryUtils.clickSlot(throwSlot, throwButton, clickType)
         capturedTimer.reset()
         discardAttempts++
         val frequency = this.frequencySetting.value!!
         if (discardAttempts >= frequency) {
            break
         }
      }
   }

   init {
      val itemRegistry: DefaultedRegistry<Item> = BuiltInRegistries.ITEM
      val defaultItems = arrayOf<Item>()
      this.itemsSetting = this.add(RegistrySetSetting<Item>("Items", itemRegistry, *defaultItems))
      this.selectionSetting = this.add(EnumSetting<ListFilterMode>("Selection", ListFilterMode.WHITELIST))
      this.delaySetting = this.add(NumberSetting<Int>("Delay", 100, 0, 1000).withUnit("ms"))
      this.frequencySetting = this.add(NumberSetting<Int>("Frequency", 1, 1, 4))
      this.offhandSetting = this.add(BooleanSetting("Offhand", false))
      this.containersSetting = this.add(BooleanSetting("Containers", false))
      this.onlyUISetting = this.add(BooleanSetting("OnlyUI", false))
      this.ignoreHotbarSetting = this.add(BooleanSetting("IgnoreHotbar", true))
      this.cleaningTimer = ElapsedTimer()
   }
}
