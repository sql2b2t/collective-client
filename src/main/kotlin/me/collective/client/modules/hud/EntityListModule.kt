package me.collective.client.modules.hud

import java.util.Set

import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.render.FontRenderer
import me.collective.client.render.ResizableHudWidgetRenderer
import me.collective.client.modules.HudWidgetModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.ListFilterMode

import com.mojang.blaze3d.vertex.PoseStack
import java.util.ArrayList
import java.util.HashMap
import java.util.Map
import java.util.regex.Matcher
import java.util.function.Predicate
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

class EntityListModule : HudWidgetModule("EntityList") {
   private val entitiesSetting: RegistrySetSetting<EntityType<*>>
   private val itemsSetting: RegistrySetSetting<Item>
   private val selectionSetting: Setting<ListFilterMode>
   private val itemSelectionSetting: Setting<ListFilterMode>
   private val sortingSetting: Setting<SortMode>
   private val customNamesSetting: Setting<Boolean>
   private val coloredCountSetting: Setting<Boolean>
   private val countsByName: MutableMap<String, Int>
   private val entries: MutableList<EntityCount>

   override fun calculateDimensions(): FloatArray {
      var totalHeight = 0.0f
      var maximumWidth = 0.0f
      for (entry in this.entries) {
         val textRenderer = FontRenderer.INSTANCE
         val coloredCount = false
         val width = textRenderer.getWidth(
            entry.formatText(coloredCount)
         )
         totalHeight += FontRenderer.INSTANCE
               .getHeight() +
         1
         if (width > maximumWidth) {
            maximumWidth = width
         }
      }

      val dimensions = FloatArray(2)
      dimensions[0] = maximumWidth
      dimensions[1] = totalHeight
      return dimensions
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(ignoredEvent: LocalPlayerTickEvent) {
      this.countsByName.clear()
      val level = ClientAccess.collectiveMinecraft.level!!
      for (entity in level.entitiesForRendering()) {
         var selection = this.selectionSetting.getValue()
         val entityType: EntityType<*> = entity.getType()
         val selectedEntityTypes: Collection<*> = this.entitiesSetting.getValue() as Collection<*>
         if (ListFilterMode.matches(selection, entityType, selectedEntityTypes)) {
            if (entity is ItemEntity) {
               val stackForEmptyCheck = entity.getItem()
               if (stackForEmptyCheck.isEmpty()) {
                  continue
               }
               val stackForItem = entity.getItem()
               val item: Item = stackForItem.getItem()
               val itemSelection = this.itemSelectionSetting.getValue()
               selection = itemSelection
               if (!ListFilterMode.matches(itemSelection, item, this.itemsSetting)) {
                  continue
               }
            }

            val useCustomNames = this.customNamesSetting.getValue()
            val entityName: String
            if (useCustomNames) {
               val nameText = entity.getName()
               entityName = nameText.getString()
            } else {
               val typeForName = entity.getType()
               val description = typeForName.getDescription()
               entityName = description.getString()
            }

            var name = entityName
            if (entity is ItemEntity) {
               val stackForName = entity.getItem()
               val hoverName = stackForName.getHoverName()
               name = hoverName.getString()
            }

            val countedAmount: Int
            if (entity is ItemEntity) {
               val stackForCount = entity.getItem()
               countedAmount = stackForCount.getCount()
            } else {
               countedAmount = 1
            }

            val countContribution = countedAmount
            this.countsByName.compute(
               name
            ) { ignoredName, previousCount -> if (previousCount == null) countContribution else previousCount + countContribution }
         }
      }

      this.entries.clear()
      this.entries.addAll(
         this.countsByName.entries.stream()
            .map { entry -> EntityCount.fromEntry(entry) }
            .toList()
      )
      val sorting = this.sortingSetting.getValue()
      when (sorting) {
         SortMode.ALPHABET -> this.entries.sortWith(Comparator.comparing { entry: EntityCount -> entry.displayName })
         SortMode.COUNT -> this.entries.sortWith(Comparator.comparing { entry: EntityCount -> -entry.count })
         SortMode.LENGTH -> this.entries.sortWith(Comparator.comparing { entry: EntityCount ->
               val textRenderer = FontRenderer.INSTANCE
               val coloredCount = false
               textRenderer.getWidth(
                  entry.formatText(coloredCount)
               )
            })
      }
   }

   override fun renderContent(poses: PoseStack) {
      val hudScale = hud.getTextScale()
      var y = this.renderer
            .getContentY(
               FontRenderer.INSTANCE
                     .getHeight() *
         hudScale
            )
         - this.renderer
            .getY()
      y /= hudScale

      for (entry in this.entries) {
         val color = this.getHudColor(y)
         val coloredCount = this.coloredCountSetting.getValue()
         val text = entry.formatText(coloredCount)
         val scaledWidth = FontRenderer.INSTANCE
               .getWidth(text) * hudScale
         val x = (
               this.renderer
                     .getContentX(scaledWidth) -
                  this.renderer
                     .getX()
            ) / hudScale
         FontRenderer.INSTANCE
            .drawWithShadow(poses, text, x, y, color)
         y += (FontRenderer.INSTANCE.getHeight() + 1) * this.renderer.getVerticalDirection()
      }
   }

   init {
      this.entitiesSetting = this.add(RegistrySetSetting("Entities", BuiltInRegistries.ENTITY_TYPE, EntityType.PLAYER))

      this.itemsSetting = this.add(RegistrySetSetting("Items", BuiltInRegistries.ITEM, Items.OBSIDIAN))

      this.selectionSetting = this.add(EnumSetting("Selection", ListFilterMode.BLACKLIST))

      this.itemSelectionSetting = this.add(EnumSetting("ItemSelection", ListFilterMode.ANY))

      this.sortingSetting = this.add(EnumSetting("Sorting", EntityListModule.SortMode.ALPHABET))

      val defaultCustomNames = true
      this.customNamesSetting = this.add(BooleanSetting("CustomNames", defaultCustomNames))

      val defaultColoredCount = true
      this.coloredCountSetting = this.add(BooleanSetting("ColoredCount", defaultColoredCount))
      this.countsByName = HashMap()
      this.entries = ArrayList()
      val itemSelectionVisible = Predicate<Any> { ignoredValue ->
         val visibilitySelection = this.selectionSetting.getValue()
         val visibilityItemType: EntityType<*> = EntityType.ITEM
         val visibleEntityTypes: Collection<*> = this.entitiesSetting.getValue() as Collection<*>
         ListFilterMode.matches(visibilitySelection, visibilityItemType, visibleEntityTypes)
      }

      this.itemSelectionSetting.addVisibilityPredicate(itemSelectionVisible)
      val widget = ResizableHudWidgetRenderer(this)
      widget.enableHudScale()
      this.setRenderer(widget)
   }

   enum class SortMode(private val displayName: String) : NamedEntry {
      ALPHABET("Alphabet"),
      COUNT("Count"),
      LENGTH("Length");

      override fun getName(): String {
         return this.displayName
      }
   }

   private data class EntityCount(
      val displayName: String,
      val count: Int
   ) {
      fun formatText(coloredCount: Boolean): String {
         return if (coloredCount) {
            val whiteCode = ChatFormatting.WHITE.toString()
            val countText = this.count.toString()
            "\u0001\u0001 x\u0001"
               .replaceFirst("\u0001", Matcher.quoteReplacement(this.displayName))
               .replaceFirst("\u0001", Matcher.quoteReplacement(whiteCode))
               .replaceFirst("\u0001", Matcher.quoteReplacement(countText))
         } else {
            val countText = this.count.toString()
            "\u0001 x\u0001"
               .replaceFirst("\u0001", Matcher.quoteReplacement(this.displayName))
               .replaceFirst("\u0001", Matcher.quoteReplacement(countText))
         }
      }

      companion object {
         fun fromEntry(entry: MutableMap.MutableEntry<String, Int>): EntityCount {
            return EntityCount(entry.key, entry.value)
         }
      }
   }
}
