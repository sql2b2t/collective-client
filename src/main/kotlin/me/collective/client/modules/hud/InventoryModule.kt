package me.collective.client.modules.hud

import me.collective.client.ClientAccess
import me.collective.client.render.RenderTargetUtils
import me.collective.client.render.GuiDrawUtils
import me.collective.client.utils.FloatAnimation
import me.collective.client.render.RenderUtils
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.PostProcessShaderPass
import me.collective.client.render.ResizableHudWidgetRenderer
import me.collective.client.modules.HudWidgetModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.ListFilterMode

import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import java.util.ArrayList
import java.util.HashMap
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.NonNullList
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

class InventoryModule : HudWidgetModule("Inventory") {
   private val selectionSetting: Setting<ListFilterMode>
   private val itemsSetting: Setting<Set<Item>>
   private val hideEmptySetting: Setting<Boolean>
   private val rowsSetting: Setting<Int>
   private val backgroundSetting: Setting<Color>
   private val outlineSetting: Setting<Color>
   private var lastStackWasBlockItem: Boolean = false
   private val visibilityAnimation: FloatAnimation

   private fun getDisplayedStacks(): Collection<ItemStack> {
      val selectionOption = this.selectionSetting
      val stacks: Collection<ItemStack>
      if (selectionOption.value == ListFilterMode.ANY) {

         stacks = this.getSlotStacks()
      } else {

         stacks = this.getSelectedItemTotals()
      }

      return stacks
   }

   private fun getSlotStacks(): Collection<ItemStack> {
      val stacks = ArrayList<ItemStack>()
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return stacks
      val inventory: Inventory = player.inventory
      val inventorySlots: NonNullList<ItemStack> = inventory.items
      var index = 0

      while (true) {
         val slotCount: Int = inventorySlots.size

         if (index >= slotCount - 9) {
            return stacks
         }

         val slotIndex = index + 9
         stacks.add(inventorySlots[slotIndex])
         index++
      }
   }

   private fun getSelectedItemTotals(): Collection<ItemStack> {
      val itemCounts = HashMap<Item, Int>()
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return emptyList()

      for (slotIndex in 0 until player.inventory.containerSize) {
         val stack = player.inventory.getItem(slotIndex)
         val item: Item = stack.item
         if (!stack.isEmpty) {
            val selectionOption = this.selectionSetting
            val selectionMode: ListFilterMode = selectionOption.value!!
            val itemsOption = this.itemsSetting
            if (ListFilterMode.matches(selectionMode, item, itemsOption)) {
               val previousCount: Int = itemCounts.getOrDefault(item, 0)
               val previousCountValue: Int = previousCount
               val totalCount: Int = previousCountValue + stack.count
               itemCounts[item] = totalCount
            }
         }
      }

      return itemCounts.entries.stream().map { entry ->
         ItemStack(entry.key, entry.value)
      }.sorted(Comparator.comparing { stack: ItemStack ->
         val item: Item = stack.item
         item.descriptionId
      }).toList()
   }

   private fun formatStackCount(stack: ItemStack): String {
      val thresholdCount: Int = stack.count

      if (thresholdCount > 9999) {
         return ">10k"
      } else {
         val thresholdCountRepeat: Int = stack.count

         if (thresholdCountRepeat > 999) {

            val countAsFloat: Float = stack.count.toFloat()

            val countInThousands: Float = countAsFloat / 1000.0f
            val countFormat = "%.1fk"
            return countFormat.format(countInThousands)
         } else {
            val count: Int = stack.count
            return count.toString()
         }
      }
   }

   private fun isEmpty(): Boolean {

      val stacks = this.getDisplayedStacks().iterator()

      while (stacks.hasNext()) {
         val stack: ItemStack = stacks.next()
         if (!stack.isEmpty) {
            val selectionOption = this.selectionSetting
            val selectionMode: ListFilterMode = selectionOption.value!!
            val item: Item = stack.item
            val itemsOption = this.itemsSetting
            if (ListFilterMode.matches(selectionMode, item, itemsOption)) {
               return false
            }
         }
      }

      return true
   }

   override fun calculateDimensions(): FloatArray {
      val sizeSelectionOption = this.selectionSetting
      val selectedSlotSize: Float
      if (sizeSelectionOption.value == ListFilterMode.ANY) {

         selectedSlotSize = 18.0f
      } else {

         selectedSlotSize = 20.0f
      }

      val slotSize: Float = selectedSlotSize
      val layoutSelectionOption = this.selectionSetting
      if (layoutSelectionOption.value != ListFilterMode.ANY) {
         val rowsOption = this.rowsSetting
         val configuredColumns: Int = rowsOption.value!!
         val maximumColumns: Int = configuredColumns

         val stackCount: Int = this.getDisplayedStacks().size

         val minimumColumns: Byte = 1
         val columnCount: Float = Mth.clamp(stackCount, minimumColumns.toInt(), maximumColumns).toFloat()

         val fractionalRows: Double = (stackCount.toFloat() / maximumColumns).toDouble()
         val roundedRows: Double = Math.ceil(fractionalRows)

         val rowCount: Float = Math.max(roundedRows, 1.0).toFloat()

         val selectedDimensions = FloatArray(2)
         selectedDimensions[0] = slotSize * columnCount
         selectedDimensions[1] = slotSize * rowCount
         return selectedDimensions
      } else {

         val slotDimensions = FloatArray(2)
         slotDimensions[0] = slotSize * 9.0f

         slotDimensions[1] = slotSize * 3.0f
         return slotDimensions
      }
   }

   override fun renderContent(poses: PoseStack) {
      val strideSelectionOption = this.selectionSetting
      val slotStride: Int
      if (strideSelectionOption.value == ListFilterMode.ANY) {

         slotStride = 18
      } else {
         slotStride = 20
      }
      val paddingSelectionOption = this.selectionSetting
      val selectedPadding: Float
      if (paddingSelectionOption.value == ListFilterMode.ANY) {

         selectedPadding = 0.5f
      } else {
         selectedPadding = 1.5f
      }
      val contentPadding: Float = selectedPadding
      val rowsOption = this.rowsSetting
      val configuredColumns: Int = rowsOption.value!!
      var columns: Int = configuredColumns
      val layoutSelectionOption = this.selectionSetting
      if (layoutSelectionOption.value == ListFilterMode.ANY) {

         columns = 9
      }
      val animationForTarget: FloatAnimation = this.visibilityAnimation

      val hasItems: Boolean = !this.isEmpty()
      animationForTarget.setTarget(hasItems, 200L)
      val animationForOpacity: FloatAnimation = this.visibilityAnimation

      var opacity: Float = animationForOpacity.getValue()
      val hideEmptyOption = this.hideEmptySetting
      val hideEmpty: Boolean = hideEmptyOption.value!!
      if (!hideEmpty) {

         opacity = 1.0f
      }

      val backgroundOption = this.backgroundSetting
      val background: Color = backgroundOption.value!!
      val backgroundAlphaOption = this.backgroundSetting
      val backgroundForAlpha: Color = backgroundAlphaOption.value!!
      val backgroundAlpha: Float = backgroundForAlpha.alpha.toFloat()

      val backgroundColor: Color = ColorUtils.withAlpha(
         background, backgroundAlpha / 255.0f * opacity
      )
      val outlineOption = this.outlineSetting
      val outline: Color = outlineOption.value!!
      val outlineAlphaOption = this.outlineSetting
      val outlineForAlpha: Color = outlineAlphaOption.value!!
      val outlineAlpha: Float = outlineForAlpha.alpha.toFloat()

      val outlineColor: Color = ColorUtils.withAlpha(
         outline, outlineAlpha / 255.0f * opacity
      )
      val dimensions: FloatArray = this.calculateDimensions()
      GuiDrawUtils.fillRect(
         poses, 0.0f, 0.0f, dimensions[0], dimensions[1], backgroundColor
      )

      val outlineX = -1.0f

      GuiDrawUtils.drawRectOutline(
         poses, outlineX, -1.0f, dimensions[0], dimensions[1], outlineColor
      )
      GuiDrawUtils.flush()
      RenderUtils.clearColor(
         PostProcessShaderPass.POST_TARGET, Color.black
      )
      RenderUtils.clearDepth(
         PostProcessShaderPass.POST_TARGET
      )
      RenderTargetUtils.overrideRenderTargets(
         PostProcessShaderPass.POST_TARGET
      )
      poses.pushPose()
      val contentDepth = 0.0f
      poses.translate(contentPadding, contentPadding, contentDepth)
      var displayIndex = 0

      val stacks = this.getDisplayedStacks().iterator()
      while (stacks.hasNext()) {
         val stack: ItemStack = stacks.next()
         val x: Int = displayIndex % columns * slotStride
         val y: Int = displayIndex / columns * slotStride

         val countText: String = this.formatStackCount(stack)
         this.renderStack(poses, stack, countText, x, y)
         displayIndex++
      }
      poses.popPose()
      RenderUtils.INSTANCE
         .flushEntities()
      RenderTargetUtils.restoreRenderTargets()
      RenderUtils.clearDepth(
         PostProcessShaderPass.POST_TARGET
      )
      RenderUtils.blitToMain(
         PostProcessShaderPass.POST_TARGET
      )
   }

   private fun renderStack(
      poses: PoseStack, stack: ItemStack, countText: String, x: Int, y: Int
   ) {
      val blockItem: Boolean = stack.item is BlockItem
      if (blockItem != this.lastStackWasBlockItem) {
         RenderUtils.INSTANCE
            .flushEntities()
      }

      this.lastStackWasBlockItem = blockItem
      poses.pushPose()
      GuiDrawUtils.drawItem(poses, stack, x.toFloat(), y.toFloat(), 0.0f, false)
      val selectionOption = this.selectionSetting
      if (selectionOption.value == ListFilterMode.ANY) {
         GuiDrawUtils.drawDurabilityBar(poses, stack, x.toFloat(), y.toFloat())
      }
      if (stack.count > 1) {
         val textRenderer: FontRenderer = FontRenderer.INSTANCE

         val countX: Float = x + 17 - FontRenderer.INSTANCE
            .getWidth(countText)

         textRenderer.drawWithShadow(
            poses, countText, countX, y + 9.0f, Color.white
         )
      }

      GuiDrawUtils.drawItemCooldown(poses, stack, x.toFloat(), y.toFloat())
      poses.popPose()
   }

   init {
      this.selectionSetting = this.add(EnumSetting<ListFilterMode>("Selection", ListFilterMode.ANY))

      this.itemsSetting = this.add(RegistrySetSetting<Item>("Items", BuiltInRegistries.ITEM))

      this.hideEmptySetting = this.add(BooleanSetting("HideEmpty", true))

      this.rowsSetting = this.add(NumberSetting<Int>(
         "Rows", 9, 1,
         9
      ))

      this.backgroundSetting = this.add(ColorSetting("Background", Color(
         10,
         10,
         10,
         50
      )))

      this.outlineSetting = this.add(ColorSetting("Outline", Color(
         10,
         10,
         10,
         100
      )))
      this.visibilityAnimation = FloatAnimation()
      this.itemsSetting.addVisibilityPredicate {
         this.selectionSetting.value != ListFilterMode.ANY
      }
      this.rowsSetting.addVisibilityPredicate {
         this.selectionSetting.value != ListFilterMode.ANY
      }
      val widget = ResizableHudWidgetRenderer(this)
      widget.enableResizing(this)
      this.setRenderer(widget)
   }

}
