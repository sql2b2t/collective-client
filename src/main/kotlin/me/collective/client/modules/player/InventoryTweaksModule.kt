package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.InputPressEvent
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.KeyUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting

import me.collective.mixin.AbstractContainerScreenAccessor
import me.collective.mixin.KeyMappingAccessor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.tags.ItemTags
import net.minecraft.tags.TagKey
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack

class InventoryTweaksModule : Module("InventoryTweaks", "Modifies your inventory actions.", ModuleCategory.PLAYER) {
   private val lockSlotSetting: Setting<Boolean>
   private val dropTimer = ElapsedTimer()
   @JvmField
   val fastMoveSetting: Setting<Boolean>
   private val onlyContainerSetting: Setting<Boolean>
   private val spamSetting: Setting<Boolean>
   @JvmField
   val fastArmorSetting: Setting<Boolean>
   @JvmField
   val bundleTweaksSetting: Setting<Boolean>
   @JvmField
   val xCarryTweaksSetting: Setting<Boolean>
   private var lastQuickMovedSlotId: Int = 0

   @EventHandler
   private fun onDropKey(event: InputPressEvent) {
      if (!this.lockSlotSetting.value!! || this.isWorldUnavailable() || event.isMouse) return
      val dropKey = (ClientAccess.collectiveMinecraft.options.keyDrop as KeyMappingAccessor).key
      if (dropKey.value != event.code || ClientAccess.collectiveMinecraft.gui.screen() != null) return
      if (ClientAccess.collectiveMinecraft.player!!.mainHandItem.maxStackSize != 1) return
      // Защита от зажатой клавиши выброса: одиночный бросок раз в 300 мс разрешён,
      // частые (спам) блокируются. Раньше было наоборот — блокировался первый бросок,
      // а спам-выброс проходил без отмены.
      if (!this.dropTimer.hasElapsed(300L)) {
         event.cancel()
         return
      }
      this.dropTimer.reset()
   }

   private fun getClickSlotId(slot: Slot): Int {
      val selectedTab = CreativeModeInventoryScreen.selectedTab
      val inventoryTab = CreativeModeTab.Type.INVENTORY == selectedTab.type
      if (ClientAccess.collectiveMinecraft.gui.screen() is CreativeModeInventoryScreen && inventoryTab) {
         return slot.containerSlot
      }
      return slot.index
   }

   fun shouldSwapBundleClickButton(slot: Slot?, mouseButton: Int): Boolean {
      if (!this.isToggled()) {
         return false
      }
      val bundleTweaksEnabled = this.bundleTweaksSetting.value!!
      if (!bundleTweaksEnabled || (mouseButton != 1 && mouseButton != 0) || slot == null) {
         return false
      }
      val carriedStack = ClientAccess.collectiveMinecraft.player!!.containerMenu.carried
      if (carriedStack.`is`(ItemTags.BUNDLES)) {
         val targetStack = slot.item
         if (!targetStack.isEmpty) {
            return true
         }
      }
      if (!carriedStack.isEmpty) {
         val targetBundleStack = slot.item
         return targetBundleStack.`is`(ItemTags.BUNDLES)
      }
      return false
   }

   @EventHandler
   private fun onMinecraftRunTickEvent(unusedEvent: MinecraftRunTickEvent) {
      val fastMoveEnabled = this.fastMoveSetting.value!!
      if (!fastMoveEnabled || ClientAccess.collectiveMinecraft.gui.screen() !is AbstractContainerScreen<*>) {
         return
      }
      if (this.onlyContainerSetting.value!! && ClientAccess.collectiveMinecraft.gui.screen() !is ContainerScreen &&
         ClientAccess.collectiveMinecraft.gui.screen() !is ShulkerBoxScreen
      ) {
         return
      }
      val carriedStack = ClientAccess.collectiveMinecraft.player!!.containerMenu.carried
      if (!carriedStack.isEmpty) {
         return
      }
      val focusedSlot = (ClientAccess.collectiveMinecraft.gui.screen() as AbstractContainerScreenAccessor).`collective$getFocusedSlot`()
      if (focusedSlot == null) {
         return
      }
      if (focusedSlot.item.isEmpty) {
         return
      }
      val spamEnabled = this.spamSetting.value!!
      if (!spamEnabled) {
         if (this.getClickSlotId(focusedSlot) == this.lastQuickMovedSlotId) {
            return
         }
      }
      var mouseButton = -1
      if (KeyUtils.isPhysicalMouseButtonDown(0)) {
         mouseButton = 0
      }
      if (KeyUtils.isPhysicalKeyDown(340) && mouseButton != -1) {
         val gameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
         val containerId = ClientAccess.collectiveMinecraft.player!!.containerMenu.containerId
         val clickedSlotId = this.getClickSlotId(focusedSlot)
         val clickType = ContainerInput.QUICK_MOVE
         val clickPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         gameMode.handleContainerInput(containerId, clickedSlotId, mouseButton, clickType, clickPlayer)
         this.lastQuickMovedSlotId = this.getClickSlotId(focusedSlot)
      }
   }

   init {
      this.lockSlotSetting = this.add(BooleanSetting("LockSlot", false))
      val fastMoveOption = BooleanSetting("FastMove", true)
      fastMoveOption.markVisibilityDependency()
      this.fastMoveSetting = this.add(fastMoveOption)
      this.onlyContainerSetting = this.add(BooleanSetting("OnlyContainer", false).dependsOn(this.fastMoveSetting))
      this.spamSetting = this.add(BooleanSetting("Spam", false).dependsOn(this.fastMoveSetting))
      this.fastArmorSetting = this.add(BooleanSetting("FastArmor", false))
      this.bundleTweaksSetting = this.add(BooleanSetting("BundleTweaks", false))
      this.xCarryTweaksSetting = this.add(BooleanSetting("XCarryTweaks", true))
   }

}
