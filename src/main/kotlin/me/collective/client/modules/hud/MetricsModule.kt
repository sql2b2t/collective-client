package me.collective.client.modules.hud

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.managers.ServerStateManager
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.utils.FrameTiming
import me.collective.client.managers.WorldStateManager
import me.collective.client.modules.HudWidgetModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.HudAnchor

import java.util.ArrayList
import java.util.Comparator
import java.util.function.Function
import java.util.function.Supplier
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BarrelBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity

class MetricsModule : HudWidgetModule("Metrics", *createAliases()) {
   private val speedSetting: Setting<Boolean>
   private val bpsSetting: Setting<Boolean>
   private val tpsSetting: Setting<Boolean>
   private val pingSetting: Setting<Boolean>
   private val fpsSetting: Setting<Boolean>
   private val serverBrandSetting: Setting<Boolean>
   private val durabilitySetting: Setting<Boolean>
   private val shulkersSetting: Setting<Boolean>
   private val chestSetting: Setting<Boolean>
   private val doubleChestSetting: Setting<Boolean>
   private val metricRows: MutableList<HudTextRow> = ArrayList()
   private var cachedDurabilityText: Component? = null
   private var chestAndBarrelCount: Int = 0
   private var shulkerCount: Int = 0

   private fun getHeldItemDurability(): Component {
      val player = ClientAccess.collectiveMinecraft.player ?: return Component.empty()
      val heldStack = player.mainHandItem

      val durabilityText = formatStackDurability(heldStack)
      if (heldStack.maxDamage == 0 && this.cachedDurabilityText != null) {
         return this.cachedDurabilityText!!
      }

      this.cachedDurabilityText = durabilityText
      return durabilityText
   }


   override fun onEnable() {
      this.metricRows.clear()
      val speedRow = HudTextRow(
         Supplier<Component> {
            val useBpsSuffix = this.bpsSetting.getValue()
            formatSpeedMetric(this, useBpsSuffix)
         },
         Supplier<Boolean> { this.speedSetting.getValue() }
      )
      val tpsRow = HudTextRow(
         Supplier<Component> {
            val networkTracker = ClientContext.serverStateManager

            val ticksPerSecond = networkTracker.getTickRate()
            Component.literal("TPS %s%.2f".format(ChatFormatting.WHITE, ticksPerSecond).replace(",", "."))
         },
         Supplier<Boolean> { this.tpsSetting.getValue() }
      )
      val pingRow = HudTextRow(
         Supplier<Component> {
            val networkTracker = ClientContext.serverStateManager

            val pingMillis = networkTracker.getLatencyMillis()
            Component.literal("Ping %s%dms".format(ChatFormatting.WHITE, pingMillis))
         },
         Supplier<Boolean> { this.pingSetting.getValue() }
      )
      val fpsRow = HudTextRow(
         Supplier<Component> {
            val frameTiming = ClientContext.frameTiming

            val framesPerSecond = frameTiming.getFramesPerSecond()
            Component.literal("FPS %s%d".format(ChatFormatting.WHITE, framesPerSecond))
         },
         Supplier<Boolean> { this.fpsSetting.getValue() }
      )
      val serverBrandRow = HudTextRow(
         Supplier<Component> {
            val connection = ClientAccess.collectiveMinecraft.player?.connection
               ?: return@Supplier Component.empty()
            Component.literal("ServerBrand %s%s".format(ChatFormatting.WHITE, connection.serverBrand()))
         },
         Supplier<Boolean> { this.serverBrandSetting.getValue() }
      )
      val durabilityRow = HudTextRow(
         Supplier<Component> { formatDurabilityMetric(this) },
         Supplier<Boolean> {
            val showDurability = this.durabilitySetting.getValue()
            if (!showDurability) {
               false
            } else {
               val player = ClientAccess.collectiveMinecraft.player ?: return@Supplier false
               val heldStack = player.mainHandItem
               heldStack.isDamageableItem
            }
         }
      )
      val chestRow = HudTextRow(
         Supplier<Component> {
            val count = this.chestAndBarrelCount
            Component.literal("Chests %s%d".format(ChatFormatting.WHITE, count))
         },
         Supplier<Boolean> { this.chestSetting.getValue() }
      )
      val shulkerRow = HudTextRow(
         Supplier<Component> {
            val count = this.shulkerCount
            Component.literal("Shulkers %s%d".format(ChatFormatting.WHITE, count))
         },
         Supplier<Boolean> { this.shulkersSetting.getValue() }
      )

      this.metricRows.addAll(
         listOf(speedRow, tpsRow, pingRow, fpsRow, serverBrandRow, durabilityRow, chestRow, shulkerRow)
      )
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(ignoredEvent: LocalPlayerTickEvent) {
      val rowsToSort = this.metricRows
      val metricWidth = Function<HudTextRow, Float> { row ->
         measureMetricWidth(FontRenderer.INSTANCE, row)
      }

      val metricComparator = Comparator.comparing<HudTextRow, Float>(metricWidth)

      rowsToSort.sortWith(metricComparator)
      this.chestAndBarrelCount = 0
      this.shulkerCount = 0
      val worldTracker = ClientContext.worldStateManager
      countContainers(this, worldTracker)
   }

   init {
      this.speedSetting = this.add(BooleanSetting("Speed", true).markVisibilityDependency())
      this.bpsSetting = this.add(BooleanSetting("BPS", false).dependsOn(this.speedSetting))
      this.tpsSetting = this.add(BooleanSetting("TPS", true))
      this.pingSetting = this.add(BooleanSetting("Ping", true))
      this.fpsSetting = this.add(BooleanSetting("FPS", true))
      this.serverBrandSetting = this.add(BooleanSetting("ServerBrand", true))
      this.durabilitySetting = this.add(BooleanSetting("Durability", false))
      this.shulkersSetting = this.add(BooleanSetting("Shulkers", false))
      this.chestSetting = this.add(BooleanSetting("Chest", false).markVisibilityDependency())
      this.doubleChestSetting = this.add(BooleanSetting("Double", false).dependsOn(this.chestSetting))
      this.setRenderer(
         HudTextListRenderer(this, this.metricRows)
      )

      this.getRenderer()
         .setAnchor(HudAnchor.BOTTOM_RIGHT)
   }

   companion object {
      private fun formatStackDurability(stack: ItemStack): Component {
         val maximumDamage = stack.maxDamage
         val remainingDurability = maximumDamage - stack.damageValue
         val remainingFraction = remainingDurability.toFloat() / maximumDamage
         var textColor = ColorUtils.redToGreen(remainingFraction).hashCode()
         if (remainingFraction.isNaN()) {
            textColor = -1
         }
         val text = remainingDurability.toString()
         val component: MutableComponent = Component.literal(text)
         val colorArgument = textColor
         return component.withColor(colorArgument)
      }

      private fun formatSpeedMetric(owner: MetricsModule, useBpsSuffix: Boolean): Component {
         val speedTemplate = "Speed %s%.2f" + (if (useBpsSuffix) "b/s" else "km/h")

         val displaySpeed = ClientContext.movementSpeedTracker.displaySpeed
         val useBpsValue = owner.bpsSetting.getValue()
         val unitDivisor: Double
         if (useBpsValue) {
            unitDivisor = 3.6
         } else {
            unitDivisor = 1.0
         }

         val speedValue = displaySpeed / unitDivisor
            val formattedText = speedTemplate.format(ChatFormatting.WHITE, speedValue)
         val text = formattedText.replace(",", ".")
         return Component.literal(text)
      }

      private fun formatDurabilityMetric(owner: MetricsModule): Component {
         val label = "Durability "
         val labelComponent: MutableComponent = Component.literal(label)

         val durabilityText = owner.getHeldItemDurability()
         return labelComponent.append(durabilityText)
      }

      private fun measureMetricWidth(textRenderer: FontRenderer, row: HudTextRow): Float {
         val rowText = row.getText()
         return -textRenderer.getWidth(rowText.getString())
      }

      private fun countContainers(owner: MetricsModule, worldTracker: WorldStateManager) {
         for (blockEntity in worldTracker.blockEntities) {
            if (blockEntity is ChestBlockEntity || blockEntity is BarrelBlockEntity) {
               owner.chestAndBarrelCount++
            }

            if (blockEntity is ShulkerBoxBlockEntity) {
               owner.shulkerCount++
            }
         }

         val combineChestPairs = owner.doubleChestSetting.getValue()
         if (combineChestPairs) {
            val combinedContainerCount = owner.chestAndBarrelCount

            owner.chestAndBarrelCount = combinedContainerCount / 2
         }
      }

      private fun createAliases(): Array<String> {
         val aliases = arrayOf("infohud", "info")
         aliases[0] = "infohud"
         aliases[1] = "info"
         return aliases
      }
   }
}
