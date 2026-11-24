package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.AbstractContainerScreenRenderTooltipEvent
import me.collective.client.events.ScreenChangeEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.SlotSetEvent
import me.collective.client.gui.screens.ShulkerPreviewScreen
import me.collective.client.utils.world.MapDataCache
import me.collective.client.utils.inventory.ItemUtils
import me.collective.client.utils.inventory.ContainerTooltipComponent
import me.collective.client.utils.KeyUtils
import me.collective.client.utils.inventory.ShulkerBoxItemPreview
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting
import me.collective.mixin.AbstractContainerScreenAccessor

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import java.awt.Color
import java.util.ArrayList
import java.util.Collections
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientBundleTooltip
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.resources.RegistryOps
import net.minecraft.tags.ItemTags
import net.minecraft.world.SimpleContainer
import net.minecraft.world.inventory.ShulkerBoxMenu
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.ShulkerBoxBlock
import org.lwjgl.glfw.GLFW

class TooltipsModule : Module {
   private val coloredSetting: Setting<Boolean>
   @JvmField
   val majorityItemSetting: Setting<Boolean>
   private val keepTextSetting: Setting<Boolean>
   private val shulkerContentSetting: Setting<Boolean>
   @JvmField
   val bundleContentSetting: Setting<Boolean>
   private val beeNestSetting: Setting<Boolean>
   @JvmField
   val mapOverlaySetting: Setting<Boolean>
   private val sizeSetting: Setting<Boolean>
   @JvmField
   val inventoryContainerSnapshots: MutableList<ShulkerBoxItemPreview>
   private var tooltipColor: Color? = null
   private var inventorySnapshotDirty: Boolean = false

   fun shouldInspectContents(stack: ItemStack): Boolean {
      if (!this.isToggled()) {
         return false
      }

      if (!KeyUtils.isPhysicalKeyDown(GLFW.GLFW_KEY_LEFT_ALT)) {
         return false
      }

      val containersEnabled = this.shulkerContentSetting.value
      if (containersEnabled) {
         val item = stack.item
         if (ItemUtils.isShulkerBox(item)) {
            return true
         }
      }

      val bundlesEnabled = this.bundleContentSetting.value
      if (bundlesEnabled) {
         val bundleTag = ItemTags.BUNDLES
         if (stack.`is`(bundleTag)) {
            return true
         }
      }

      return false
   }

   fun getTooltipColor(): Color? {
      return if (!(this.coloredSetting.value)) null else this.tooltipColor
   }

   fun setTooltipColor(color: Color?) {
      this.tooltipColor = color
   }

   @EventHandler
   private fun onMinecraftDisconnectEvent(event: MinecraftDisconnectEvent) {

      this.setTooltipColor(null)
   }

   @EventHandler
   private fun onAbstractContainerScreenRenderTooltipEvent(
      event: AbstractContainerScreenRenderTooltipEvent
   ) {
      if (event.screen is AbstractContainerScreen<*>) {
         val focusedSlot = (event.screen as AbstractContainerScreenAccessor)
            .`collective$getFocusedSlot`()
         if (focusedSlot != null) {
            val hoveredStack: ItemStack = focusedSlot.item
            val containerItems: NonNullList<ItemStack> = NonNullList.withSize(SHULKER_SLOT_COUNT, ItemStack.EMPTY)
            val tooltipComponents =
               event.tooltipComponents as java.util.List<ClientTooltipComponent>
            run colorSelection@{
               if (hoveredStack.item is BlockItem) {
                  val blockItem = hoveredStack.item as BlockItem
                  if (blockItem.block is ShulkerBoxBlock) {
                     val shulkerBlock = blockItem.block as ShulkerBoxBlock
                     if (shulkerBlock.color != null) {
                        val baseColor = Color(shulkerBlock.color!!.mapColor.col, false)
                        val rgbComponents = FloatArray(3)
                        baseColor.getColorComponents(rgbComponents)
                        val capturedTooltipColor = Color(
                           rgbComponents[0], rgbComponents[1], rgbComponents[2], 0.75F
                        )

                        this.setTooltipColor(capturedTooltipColor)
                        return@colorSelection
                     }
                  }
               }

               this.setTooltipColor(null)
            }

            val bundleComponent = BundleTooltipComponent.fromSlot(focusedSlot)
            if (bundleComponent != null) {
               val bundlesEnabled = this.bundleContentSetting.value
               if (bundlesEnabled) {
                  tooltipComponents.removeIf { component -> component is ClientBundleTooltip }
                  tooltipComponents.add(bundleComponent)
               }
            }

            val containerComponentType = DataComponents.CONTAINER
            val containedItems = hoveredStack.get(containerComponentType)
            if (containedItems != null) {
               val contentList = containedItems.stream().toList()

               val slotCount = Math.min(contentList.size, containerItems.size)
               for (contentIndex in 0 until slotCount) {
                  containerItems.set(contentIndex, contentList[contentIndex])
               }
            }

            this.readLegacyContainerItems(hoveredStack, containerItems)
            if (!containerItems.stream().allMatch(ItemStack::isEmpty)) {
               val contentsEnabled = this.shulkerContentSetting.value
               if (contentsEnabled) {
                  val windowHandle = ClientAccess.collectiveMinecraft.window.handle()

                  if (GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS) {
                     if (!(
                        event.screen is ShulkerPreviewScreen
                        )) {
                        val capturedScreenClient = ClientAccess.collectiveMinecraft
                        val localPlayer = capturedScreenClient.player
                        if (localPlayer != null) {
                           val menuInventory = localPlayer.inventory

                           val previewMenu = ShulkerBoxMenu(
                              1337, menuInventory, SimpleContainer(
                                 *(containerItems.toArray(arrayOfNulls<ItemStack>(SHULKER_SLOT_COUNT)) as Array<ItemStack>)
                              )
                           )
                           val previewInventory = localPlayer.inventory
                           val previewTitle = hoveredStack.hoverName
                           val previewScreen = ShulkerPreviewScreen(
                              previewMenu, previewInventory, previewTitle, event.screen
                           )
                           capturedScreenClient.setScreen(previewScreen)
                        }
                     }
                  }

                  val keepContentsText = this.keepTextSetting.value
                  if (keepContentsText) {
                     tooltipComponents.add(ContainerTooltipComponent(containerItems))
                  } else {
                     tooltipComponents.clear()
                     val containerTitle = hoveredStack.hoverName
                     val containerTitleText = containerTitle.visualOrderText
                     val capturedContainerTitle = ClientTooltipComponent.create(containerTitleText)
                     val containerComponent = ContainerTooltipComponent(containerItems)
                     tooltipComponents.addAll(java.util.List.of(capturedContainerTitle, containerComponent))
                  }
               }
            }

            if (hoveredStack.item == Items.FILLED_MAP) {
               val mapComponentType = DataComponents.MAP_ID
               val mapIdData = hoveredStack.get(mapComponentType)
               val resolvedMapId: Int
               if (mapIdData == null) {
                  resolvedMapId = -1
               } else {
                  resolvedMapId = mapIdData.id()
               }

               val mapId = resolvedMapId
               val mapCache = ClientContext.mapDataCache
               val mapData = mapCache.getMapData(hoveredStack, mapId)
               if (mapData != null) {
                  val keepMapText = this.keepTextSetting.value
                  if (!keepMapText) {
                     tooltipComponents.clear()
                     val mapTitle = hoveredStack.hoverName
                     val mapTitleText = mapTitle.visualOrderText
                     val capturedMapTitle = ClientTooltipComponent.create(mapTitleText)
                     val mapComponent = MapTooltipComponent(mapId, mapData)
                     tooltipComponents.addAll(java.util.List.of(capturedMapTitle, mapComponent))
                  } else {
                     tooltipComponents.add(MapTooltipComponent(mapId, mapData))
                  }

                  val overlayEnabled = this.mapOverlaySetting.value
                  if (overlayEnabled) {

                     val overlayHintText = "Hold Left-Alt to hide item amount."
                     var overlayHint: MutableComponent = Component.literal(overlayHintText)
                     overlayHint = overlayHint.withStyle { style: Style ->
                        style.applyFormat(ChatFormatting.YELLOW)
                     }
                     val hintText = overlayHint.visualOrderText
                     tooltipComponents.add(ClientTooltipComponent.create(hintText))
                  }
               }
            }

            val bees = hoveredStack.get(DataComponents.BEES)
            if (bees != null) {
               val beesEnabled = this.beeNestSetting.value
               if (beesEnabled) {
                  val occupants = bees.bees()
                  val beeIterator = occupants.iterator()

                  while (beeIterator.hasNext()) {
                     val beeOccupant = beeIterator.next()
                     val entityData = beeOccupant.entityData()
                     val beeTag = entityData.copyTagWithoutId()

                     if (beeTag.contains("FlowerPos")) {

                        val flowerPositionCodec: Codec<BlockPos> = BlockPos.CODEC
                        val flowerResult = beeTag.read("flower_pos", flowerPositionCodec)
                        val flowerPosition: BlockPos? = flowerResult.orElse(null)
                        if (flowerPosition != null) {
                           val flowerText = "Position " + flowerPosition.toShortString()
                           val flowerComponent = Component.literal(flowerText)
                           val flowerTextSequence = flowerComponent.visualOrderText
                           tooltipComponents.add(ClientTooltipComponent.create(flowerTextSequence))
                        }
                     }
                  }
               }
            }

            val serializedSizeEnabled = this.sizeSetting.value
            val tooltipPlayer = ClientAccess.collectiveMinecraft.player
            if (serializedSizeEnabled && tooltipPlayer != null) {
               val counter = CountingDataOutput()
               val tooltipRegistries = tooltipPlayer.registryAccess()
               val tooltipNbtOps = NbtOps.INSTANCE
               val registryOps: RegistryOps<Tag> = tooltipRegistries.createSerializationContext(tooltipNbtOps)
               val itemCodec: Codec<ItemStack> = ItemStack.CODEC
               val encodedItem = itemCodec.encodeStart(registryOps, hoveredStack)
               if (encodedItem is DataResult.Success<*>) {
                  try {
                     val serializedItem = encodedItem.value() as Tag
                     serializedItem.write(counter)

                     val serializedBytes = counter.byteCount.toLong()
                     val formattedSize = this.formatSize(serializedBytes)
                     val sizeComponent = Component.literal(formattedSize)
                     val sizeText = sizeComponent.visualOrderText
                     tooltipComponents.add(ClientTooltipComponent.create(sizeText))
                  } catch (ignoredFailure: Throwable) {
                  }
               }
            }
         }
      }
   }

   fun renderItemPreview(graphics: GuiGraphics, stack: ItemStack, x: Float, y: Float) {
      val pose = graphics.pose()
      pose.pushMatrix()
      pose.scale(ITEM_PREVIEW_SCALE, ITEM_PREVIEW_SCALE)
      pose.translate(ITEM_PREVIEW_OFFSET_X, 0.0F)
      val itemX = (x / ITEM_PREVIEW_SCALE).toInt()
      val itemY = (y / ITEM_PREVIEW_SCALE).toInt()
      graphics.renderItem(stack, itemX, itemY)
      pose.popMatrix()
   }

   @EventHandler
   private fun onScreenChange(event: ScreenChangeEvent) {
      if (event.newScreen is AbstractContainerScreen<*>) {
         this.inventorySnapshotDirty = true
      }
   }

   @EventHandler
   private fun onSlotSetEvent(event: SlotSetEvent) {
      this.inventorySnapshotDirty = true
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      val screen = ClientAccess.collectiveMinecraft.screen
      if (!(screen is AbstractContainerScreen<*>) || (screen as AbstractContainerScreenAccessor).`collective$getFocusedSlot`() == null) {

         this.setTooltipColor(null)
      }
   }

   private fun formatSize(bytes: Long): String {
      val kilobytes = bytes / 1024L
      val megabytes = kilobytes / 1024L
      if (megabytes > 0L) {
         return String.format("%,d MB", megabytes)
      } else if (kilobytes > 0L) {
         return String.format("%,d KB", kilobytes)
      } else {
         return String.format("%,d B", bytes)
      }
   }

   @EventHandler
   private fun onMinecraftRunTickEvent(event: MinecraftRunTickEvent) {
      if (!this.inventorySnapshotDirty) return
      val player = ClientAccess.collectiveMinecraft.player ?: return
      this.inventoryContainerSnapshots.clear()
      val menuSlots = player.containerMenu.slots

      for (slot in menuSlots) {
         val capturedStack = slot.getItem()

         val snapshot = ShulkerBoxItemPreview.create(capturedStack)
         if (snapshot != null) {
            this.inventoryContainerSnapshots.add(snapshot)
         }
      }

      this.inventorySnapshotDirty = false
   }

   private fun readLegacyContainerItems(stack: ItemStack, slots: MutableList<ItemStack>) {
      val player = ClientAccess.collectiveMinecraft.player ?: return
      val registries = player.registryAccess()
      val registryOps: RegistryOps<Tag> = registries.createSerializationContext(NbtOps.INSTANCE)
      val customData = stack.get(DataComponents.CUSTOM_DATA)
      if (customData == null) {
         return
      }

      val rootTag = customData.copyTag()
      if (!rootTag.contains("BlockEntityTag")) {
         return
      }
      val blockEntityTag = rootTag.getCompoundOrEmpty("BlockEntityTag")
      if (!blockEntityTag.contains("Items")) {
         return
      }

      val itemTags = blockEntityTag.getListOrEmpty("Items")
      val slotCount = Math.min(itemTags.size, slots.size)
      for (index in 0 until slotCount) {
         val itemTag = itemTags.getCompoundOrEmpty(index)
         val itemId = itemTag.getStringOr("id", "minecraft:air")
         if (itemId == "minecraft:air") {
            continue
         }

         val parsed = ItemStack.CODEC.parse(registryOps, itemTag)
         if (parsed is DataResult.Success<*>) {
            val item = parsed.value() as ItemStack
            if (itemTag.contains("Count")) {
               val count = itemTag.getByteOr("Count", 0.toByte())
               if (count <= 0) {
                  continue
               }
               item.setCount(count.toInt())
            }
            slots.set(index, item)
         }
      }
   }

   init {
      this.coloredSetting = this.add(BooleanSetting("Colored", true))
      this.majorityItemSetting = this.add(BooleanSetting("MajorityItem", false))
      this.keepTextSetting = this.add(BooleanSetting("KeepText", true))
      this.shulkerContentSetting = this.add(BooleanSetting("ShulkerContent", true))
      this.bundleContentSetting = this.add(BooleanSetting("BundleContent", true))
      this.beeNestSetting = this.add(BooleanSetting("BeeNest", false))
      this.mapOverlaySetting = this.add(BooleanSetting("MapOverlay", true))
      this.sizeSetting = this.add(BooleanSetting("Size", false))
      this.inventoryContainerSnapshots = Collections.synchronizedList(ArrayList())
      this.setDrawn(false)
   }

   companion object {
      private const val SHULKER_SLOT_COUNT = 27
      private const val ITEM_PREVIEW_SCALE = 0.625F
      private const val ITEM_PREVIEW_OFFSET_X = 11.0F

      private fun createTooltipDescription(): String {
         val previewColor = ChatFormatting.YELLOW.toString()
         val inspectColor = ChatFormatting.YELLOW.toString()
         return "Draws advanced tool tips on items.\n" + previewColor +
            "Middle Click a shulker to open the preview screen.\n" + inspectColor +
            "Hold Left Alt when hovering over a shulker to inspect it's items."
      }
   }

   constructor() : super("Tooltips", createTooltipDescription(), ModuleCategory.RENDER)
}
