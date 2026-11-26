package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.managers.KitRegistry
import me.collective.client.utils.inventory.InventorySortStrategy
import me.collective.client.managers.InventoryStateManager
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.combat.MainhandModule
import me.collective.client.modules.exploit.MultiTaskModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.InventorySorterModeChoice
import me.collective.client.settings.options.InventorySorterScopeChoice

import java.util.stream.IntStream
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

class InventorySorterModule : Module("InventorySorter", "Sorts items in your inventory.", ModuleCategory.PLAYER) {

   companion object {
      @JvmStatic
      private var multiTaskModule: MultiTaskModule? = null
      @JvmStatic
      private var mainhandModule: MainhandModule? = null

      @JvmStatic
      fun bindModules(moduleRegistry: ModuleRegistry) {
         multiTaskModule = moduleRegistry.getModuleOrNull(MultiTaskModule::class.java)
         mainhandModule = moduleRegistry.getModuleOrNull(MainhandModule::class.java)
      }
   }

   val scopeSetting: Setting<InventorySorterScopeChoice>
   val modeSetting: Setting<InventorySorterModeChoice>
   val delaySetting: Setting<Int>
   val frequencySetting: Setting<Int>
   val silentSetting: Setting<Boolean>
   val autoDisableSetting: Setting<Boolean>
   val sortTimer: ElapsedTimer

   init {
      val defaultScope = InventorySorterScopeChoice.INVENTORY
      this.scopeSetting = this.add(EnumSetting("Scope", defaultScope))
      val defaultMode = InventorySorterModeChoice.TYPE
      this.modeSetting = this.add(EnumSetting("Mode", defaultMode))
      this.delaySetting = this.add(NumberSetting("Delay", 50, 50, 500).withUnit("ms"))
      this.frequencySetting = this.add(NumberSetting("Frequency", 1, 1, 3))
      this.silentSetting = this.add(BooleanSetting("Silent", false))
      this.autoDisableSetting = this.add(BooleanSetting("AutoDisable", false))
      this.sortTimer = ElapsedTimer()
   }

   fun getTargetSlots(scope: InventorySorterScopeChoice, mode: InventorySorterModeChoice): IntStream {
      if (scope == InventorySorterScopeChoice.BOTH && mode != InventorySorterModeChoice.KIT) {
         val firstStart = scope.startInclusive
         val firstEnd = scope.endExclusive
         val inventoryFirst = IntStream.range(firstStart, firstEnd).skip(9L)
         val secondStart = scope.startInclusive
         val secondEnd = scope.endExclusive
         val hotbarLast = IntStream.range(secondStart, secondEnd).limit(9L)
         return IntStream.concat(inventoryFirst, hotbarLast)
      }
      val startInclusive = scope.startInclusive
      val endExclusive = scope.endExclusive
      return IntStream.range(startInclusive, endExclusive)
   }

   @EventHandler
   fun onLocalPlayerTick(event: LocalPlayerPreTickEvent) {
      if (shouldSkipSorting()) {
         return
      }

      val mainhand = mainhandModule
      val protectedSlot = mainhand?.getConfiguredHotbarSlot() ?: -1
      val mode = this.modeSetting.value
      val scope = this.scopeSetting.value
      var sourceSlots: Array<Int> = mode.strategy.getSourceSlots(scope)
      val targetSlots: IntArray = getTargetSlots(scope, mode).toArray()
      var attemptedSwaps = 0
      for (pairIndex in 0 until Math.min(sourceSlots.size, targetSlots.size)) {
         if (mainhand != null && mainhand.isToggled()) {
            val protectedSource = sourceSlots[pairIndex]
            if (protectedSlot == protectedSource || protectedSlot == targetSlots[pairIndex]) {
               continue
            }
         }

         val sourceSlot = sourceSlots[pairIndex]
         val sourceStack = InventorySortStrategy.getInventoryStack(sourceSlot)
         val targetSlot = targetSlots[pairIndex]
         val targetStack = InventorySortStrategy.getInventoryStack(targetSlot)
         if (sourceSlots[pairIndex] != targetSlots[pairIndex] && !ItemStack.isSameItem(sourceStack, targetStack)) {
            val targetInventorySlot = targetSlots[pairIndex]
            val targetMenuSlot = InventoryUtils.toMenuSlot(targetInventorySlot)
            val sourceInventorySlot = sourceSlots[pairIndex]
            val sourceMenuSlot = InventoryUtils.toMenuSlot(sourceInventorySlot)
            executeSlotSwap(targetMenuSlot, sourceMenuSlot)
            attemptedSwaps++
         }

         val frequency = this.frequencySetting.value
         if (attemptedSwaps >= frequency) {
            break
         }
         sourceSlots = mode.strategy.getSourceSlots(scope)
      }
      if (attemptedSwaps == 0) {
         if (this.autoDisableSetting.value) {
            disable()
         }
      }
   }

   fun executeSlotSwap(firstSlot: Int, secondSlot: Int) {
      val initialManager = ClientContext.inventoryStateManager
      if (InventoryStateManager.wouldReachClickLimit(initialManager, 1)) {
         return
      }
      this.sortTimer.reset()

      if (firstSlot >= 36) {
         if (secondSlot >= 36) {
            val hotbarButton = secondSlot - 36
            InventoryUtils.swapWithHotbarSlot(firstSlot, hotbarButton)
            return
         }
      }

      if (firstSlot >= 36) {
         val firstButton = firstSlot - 36
         InventoryUtils.swapWithHotbarSlot(secondSlot, firstButton)
      } else {
         if (secondSlot >= 36) {
            val secondButton = secondSlot - 36
            InventoryUtils.swapWithHotbarSlot(firstSlot, secondButton)
         } else {
            val fallbackManager = ClientContext.inventoryStateManager
            if (!InventoryStateManager.wouldReachClickLimit(fallbackManager, 3)) {
               InventoryUtils.swapSlotsWithPickup(firstSlot, secondSlot)
            }
         }
      }
   }

   fun shouldSkipSorting(): Boolean {
      val carriedStack = InventoryUtils.getCarriedStack()
      if (!carriedStack.isEmpty) {
         return true
      }
      val multitask = multiTaskModule
      if (multitask != null && multitask.shouldPauseModule(this)) {
         return true
      }
      if (ClientAccess.collectiveMinecraft.player!!.inventoryMenu != ClientAccess.collectiveMinecraft.player!!.containerMenu) {
         return true
      }
      if (this.modeSetting.value == InventorySorterModeChoice.KIT) {
         val kitOwner = ClientContext.kitRegistry
         if (KitRegistry.getCurrentKit(kitOwner) == null) {
            return true
         }
      }
      val silent = this.silentSetting.value
      if (!silent && ClientAccess.collectiveMinecraft.gui.screen() !is AbstractContainerScreen<*>) {
         return true
      }
      val timer = this.sortTimer
      val delayMillis = this.delaySetting.value.toLong()
      return !timer.hasElapsed(delayMillis)
   }
}
