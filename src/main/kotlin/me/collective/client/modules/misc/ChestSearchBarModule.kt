package me.collective.client.modules.misc

import me.collective.client.ClientAccess
import me.collective.client.events.AbstractContainerScreenEvent
import me.collective.client.events.EventHandler
import me.collective.client.render.RenderUtils
import me.collective.client.utils.KeyUtils
import me.collective.client.utils.inventory.ChestSearchController
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.item.ItemStack

class ChestSearchBarModule : Module("ChestSearchBar", "Highlights items in chests and shulker boxes.", ModuleCategory.MISC) {
   val highlightFullSetting: Setting<Boolean>
   private val chestStealerSetting: Setting<Boolean>
   private val onlyFullSetting: Setting<Boolean>

   init {
      val highlightFullDefault = false
      this.highlightFullSetting = this.add(BooleanSetting(("HighlightFull"), highlightFullDefault))
      val chestStealerDefault = false
      val chestStealerOption = BooleanSetting(("ChestStealer"), chestStealerDefault)
      chestStealerOption.markVisibilityDependency()
      this.chestStealerSetting = this.add(chestStealerOption)
      val onlyFullDefault = false
      val onlyFullOption = BooleanSetting(("OnlyFull"), onlyFullDefault)
      this.onlyFullSetting = this.add(onlyFullOption.dependsOn(this.chestStealerSetting))
      val searchController = ChestSearchController.INSTANCE
      searchController.init()

      this.setDrawn(false)
   }

   fun match(stack: ItemStack, requireFull: Boolean): Boolean {
      val searchBox = ChestSearchController.searchBox
      if (searchBox == null) {
         return true
      }

      val query: String = searchBox.value
      if (query.isEmpty()) {
         return true
      }

      if (requireFull && !ChestSearchController.isFull(stack, query)) {
         return false
      }

      return ChestSearchController.namesMatch(stack, query)
   }

   fun shouldSkip(stack: ItemStack): Boolean {
      if (!this.isToggled()) {
         return false
      }
      val chestStealerSettingSnapshot = this.chestStealerSetting
      val filterChestStealer: Boolean = chestStealerSettingSnapshot.value
      if (!filterChestStealer) {
         return false
      }
      val onlyFullSettingSnapshot = this.onlyFullSetting
      val requireFullSnapshot: Boolean = onlyFullSettingSnapshot.value

      return !this.match(stack, requireFullSnapshot)
   }

   @EventHandler
   private fun onRender(event: AbstractContainerScreenEvent) {
      val searchControllerSnapshot = ChestSearchController.INSTANCE
      val graphicsSnapshot = event.graphics!!
      val screenSnapshot = event.screen
      val poseSnapshot = event.poseStack!!
      val mouseXSnapshot = event.mouseX
      val mouseYSnapshot = event.mouseY
      val renderValueSnapshot = RenderUtils.getPartialTick()

      searchControllerSnapshot.onDrawGui(graphicsSnapshot, screenSnapshot, poseSnapshot, mouseXSnapshot, mouseYSnapshot, renderValueSnapshot)
      if (ChestSearchController.searchBox != null) {

         if (KeyUtils.isPhysicalKeyDown(70)) {

            if (KeyUtils.isControlDown()) {
               val focusScreen = ClientAccess.collectiveMinecraft.screen!!
               val focusTarget = ChestSearchController.searchBox
               focusScreen.setFocused(focusTarget)
            }
         }

         if (KeyUtils.isPhysicalKeyDown(257)) {
            val focusScreen = ClientAccess.collectiveMinecraft.screen!!
            focusScreen.setFocused(null)
         }
      }
   }

}
