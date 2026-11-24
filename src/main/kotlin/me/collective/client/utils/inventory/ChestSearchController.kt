package me.collective.client.utils.inventory

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientContext
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.misc.ChestSearchBarModule
import me.collective.client.render.ColorUtils
import me.collective.mixin.AbstractContainerScreenAccessor
import it.unimi.dsi.fastutil.objects.Object2IntMap
import java.awt.Color
import java.util.Locale
import java.util.function.BiPredicate
import java.util.regex.Pattern
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.HopperScreen
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen
import net.minecraft.client.resources.language.I18n
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.component.ItemContainerContents
import net.minecraft.world.item.component.BundleContents

class ChestSearchController {
   companion object {
      private const val SLOT_SIZE = 16
      private val DIM_COLOR: Int = 0xAA000000.toInt()
      private const val BACKGROUND_COLOR = 0x4B000000
      private const val BORDER_COLOR = 0x71000000
      private val NO_MATCH_TEXT_COLOR: Int = 0xFFFF5555.toInt()
      private val replaymodLoaded: Boolean = FabricLoader.getInstance().isModLoaded("replaymod")
      @JvmField
      val INSTANCE: ChestSearchController = ChestSearchController()
      private lateinit var chestSearchBarModule: ChestSearchBarModule
      @JvmField
      var searchBox: EditBox? = null
      @JvmField
      var text: String = ""
      @JvmField
      var matchedSlotCount: Int = 0

      @JvmStatic
      fun isFull(stack: ItemStack, query: String): Boolean {
         if (!chestSearchBarModule.highlightFullSetting.value) return false
         val normalizedQuery: String? = normalize(query)
         if (normalizedQuery == null || normalizedQuery.isEmpty() || stack.isEmpty) return false
         if (!stack.item.descriptionId.contains("shulker_box")) return false
         val contents = stack.get(DataComponents.CONTAINER)
         if (contents == null || contents.stream().toList().size != 27) return false
         for (containedStack in contents.stream().toList()) {
            if (!namesMatch(containedStack, normalizedQuery)) return false
         }
         return true
      }

      @JvmStatic
      fun namesMatch(stack: ItemStack, query: String): Boolean {
         val normalizedQuery: String? = normalize(query)
         if (normalizedQuery == null || normalizedQuery.isEmpty()) return true
         if (stack.isEmpty) return false
         val item = stack.item
         if (matchesContainerContents(stack, normalizedQuery)) return true
         if (matchesBundleContents(stack, normalizedQuery)) return true
         val hoverName: String? = normalize(stack.hoverName.string)
         var predicate: BiPredicate<String, String> = BiPredicate { candidate, expression -> candidate.contains(expression) }
         var currentQuery: String = normalizedQuery
         if (currentQuery.length >= 3 && currentQuery.startsWith("\"") && currentQuery.endsWith("\"")) {
            currentQuery = currentQuery.substring(1, currentQuery.length - 1)
            predicate = BiPredicate { candidate, expression -> candidate == expression }
         }
         if (currentQuery.length >= 3 && currentQuery.startsWith("/") && currentQuery.endsWith("/")) {
            currentQuery = currentQuery.substring(1, currentQuery.length - 1)
            predicate = BiPredicate { candidate, expression ->
               try {
                  Pattern.compile(expression).matcher(candidate).find()
               } catch (malformed: java.util.regex.PatternSyntaxException) {
                  // Некорректный regex (например «/(/») не должен ронять отрисовку слотов —
                  // откатываемся к обычному подстрочному поиску.
                  candidate.contains(expression)
               }
            }
         }
         val storedEnchantments = stack.get(DataComponents.STORED_ENCHANTMENTS)
         if (storedEnchantments != null) {
            for (entry in storedEnchantments.entrySet()) {
               if (matchesEnchantment(predicate, currentQuery, entry)) return true
            }
         }
         if (stack.isEnchanted) {
            for (entry in stack.enchantments.entrySet()) {
               if (matchesEnchantment(predicate, currentQuery, entry)) return true
            }
         }
         if (predicate.test(I18n.get(item.descriptionId).lowercase(Locale.ROOT), currentQuery)) return true
         val potionContents = stack.get(DataComponents.POTION_CONTENTS)
         if (potionContents != null) {
            for (effect in potionContents.allEffects) {
               if (predicate.test(I18n.get(effect.descriptionId).lowercase(Locale.ROOT), currentQuery)) return true
            }
         }
         return predicate.test(hoverName!!, currentQuery)
      }

      private fun normalize(value: String): String? {
         return ChatFormatting.stripFormatting(value.trim().lowercase(Locale.ROOT))
      }

      private fun matchesContainerContents(stack: ItemStack, query: String): Boolean {
         val contents = stack.get(DataComponents.CONTAINER)
         if (contents != null) {
            for (containedStack in contents.nonEmptyItems()) {
               if (namesMatch(containedStack, query)) return true
            }
         }
         return false
      }

      private fun matchesBundleContents(stack: ItemStack, query: String): Boolean {
         val contents = stack.get(DataComponents.BUNDLE_CONTENTS)
         if (contents != null) {
            for (containedStack in contents.itemsCopy()) {
               if (namesMatch(containedStack, query)) return true
            }
         }
         return false
      }

      private fun matchesEnchantment(
         predicate: BiPredicate<String, String>,
         query: String,
         entry: Object2IntMap.Entry<Holder<Enchantment>>?
      ): Boolean {
         if (entry == null) return false
         val name = Enchantment.getFullname(entry.key, entry.intValue) as MutableComponent
         val searchableName = name.toString().lowercase(Locale.ROOT).replace(Regex("enchantment"), "")
         return predicate.test(searchableName, query)
      }

      @JvmStatic
      fun bindReferencedModules(registry: ModuleRegistry) {
         chestSearchBarModule = registry.getModule(ChestSearchBarModule::class.java) as ChestSearchBarModule
      }
   }

   fun init() {
      searchBox = null
      text = ""
      ScreenEvents.AFTER_INIT.register(SearchScreenInitializer())
   }

   fun onDrawGui(graphics: GuiGraphics, screen: Screen, poseStack: PoseStack, mouseX: Float, mouseY: Float, partialTick: Float) {
      val box = searchBox
      if (!chestSearchBarModule.isToggled() || box == null) return
      poseStack.pushPose()
      graphics.nextStratum()
      val accessor = screen as AbstractContainerScreenAccessor
      if (!box.value.isBlank() && screen is AbstractContainerScreen<*>) {
         val menu = screen.menu
         val containerX = accessor.x
         val containerY = accessor.y
         matchedSlotCount = 0
         for (slot in menu.slots) {
            val slotX = containerX + slot.x
            val slotY = containerY + slot.y
            val stack = slot.item
            if (!namesMatch(stack, box.value)) {
               graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, DIM_COLOR)
            } else {
               matchedSlotCount++
            }
            if (isFull(stack, box.value)) {
               val color = ColorUtils.toArgb(Color.green, 100)
               graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, color)
            }
         }
      }
      if (matchedSlotCount == 0 && !box.value.isEmpty()) {
         box.setTextColor(NO_MATCH_TEXT_COLOR)
      } else {
         box.setTextColor(-1)
      }
      graphics.fill(box.x + 2, box.y, box.right, box.bottom, BACKGROUND_COLOR)
      graphics.hLine(box.x + 2, box.right, box.bottom, -1)
      graphics.vLine(box.right, box.y, box.bottom, -1)
      graphics.nextStratum()
      graphics.hLine(box.x + 2, box.right, box.y, BORDER_COLOR)
      graphics.vLine(box.x + 2, box.y, box.bottom + 1, BORDER_COLOR)
      box.render(graphics, mouseX.toInt(), mouseY.toInt(), partialTick)
      poseStack.popPose()
   }

   class SearchScreenInitializer : ScreenEvents.AfterInit {
      override fun afterInit(minecraft: Minecraft, screen: Screen, scaledWidth: Int, scaledHeight: Int) {
         if (!chestSearchBarModule.isToggled()) {
            searchBox = null
            return
         }
         if (replaymodLoaded && screen.javaClass.packageName.startsWith(REPLAYMOD_CONTAINER_PACKAGE)) return
         if (screen !is ContainerScreen && screen !is ShulkerBoxScreen && screen !is HopperScreen) {
            searchBox = null
            return
         }
         val client = Minecraft.getInstance()
         val containerScreen = screen as AbstractContainerScreen<*>
         val accessor = screen as AbstractContainerScreenAccessor
         val font: Font = client.font
         val boxX = accessor.x + 77
         val boxY = accessor.y + 4
         val box = EditBox(font, boxX, boxY, 91, 10, Component.literal(""))
         searchBox = box
         box.value = text
         box.isFocused = false
         box.setMaxLength(32)
         box.isBordered = true
         box.setResponder { newText -> text = newText }
         Screens.getButtons(screen).add(box)
      }

      companion object {
         private const val REPLAYMOD_CONTAINER_PACKAGE = "com.replaymod.lib.de.johni0702.minecraft.gui.container"
      }
   }
}
