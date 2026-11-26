package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.RotationRequestEvent
import me.collective.client.events.EventHandler
import me.collective.client.managers.RotationManager
import me.collective.client.utils.ElapsedTimer
import me.collective.client.render.FeatureBoxRenderer
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.BlockSampleMode
import me.collective.client.settings.options.ListFilterMode

import java.awt.Color
import java.util.function.Predicate
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.Vec3

class SourceFillModule : Module("SourceFill", "Replaces liquid blocks with solid blocks.", ModuleCategory.PLAYER) {
   private val selectionSetting: Setting<ListFilterMode>
   private val blocksSetting: Setting<Set<Block>>
   private val rangeSetting: Setting<Float>
   private val wallRangeSetting: Setting<Float>
   private val delaySetting: Setting<Int>
   private val blocksPerTickSetting: Setting<Int>
   private val airPlaceSetting: Setting<Boolean>
   private val rotateSetting: Setting<Boolean>
   private val sourcesSetting: Setting<Boolean>
   private val waterSetting: Setting<Boolean>
   private val lavaSetting: Setting<Boolean>
   private val renderSetting: Setting<Boolean>
   private val fillSetting: Setting<Color>
   private val outlineSetting: Setting<Color>
   private val lineWidthSetting: Setting<Float>
   private val spongeExpiryByPosition: MutableMap<BlockPos, Long>
   private val placementTimer: ElapsedTimer

   private fun isOutsideSpongeRadius(position: BlockPos): Boolean {
      val spongeIterator = this.spongeExpiryByPosition.keys.iterator()

      while (spongeIterator.hasNext()) {
         val spongeCenter: Vec3 = Vec3.atCenterOf(spongeIterator.next())
         val targetCenter: Vec3 = Vec3.atCenterOf(position)
         if (spongeCenter.distanceTo(targetCenter) <= 4.0) {
            return false
         }
      }

      return true
   }

   private fun isWithinPlacementReach(position: BlockPos): Boolean {
      val sampleMode: BlockSampleMode = BlockSampleMode.X4
      if (WorldUtils.canSeeAny(sampleMode.sampleBlock(position))) {
         return true
      }

      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val eyePosition: Vec3 = player.eyePosition
      val blockCenter: Vec3 = Vec3.atCenterOf(position)
      val distance: Double = eyePosition.distanceTo(blockCenter)
      val wallRangeValue: Float = this.wallRangeSetting.value
      return distance <= wallRangeValue.toDouble()
   }

   private fun collectTargetPositions(): List<BlockPos> {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val eyePosition: Vec3 = player.eyePosition
      val radius: Float = this.rangeSetting.value
      return WorldUtils.streamSphereBlocks(eyePosition, radius).filter(
         { fluidPosition: BlockPos ->
            val level = ClientAccess.collectiveMinecraft.level!!
            level.isFluidAtPosition(fluidPosition, this::isSelectedFluid)
         }
      ).filter(
         { position: BlockPos ->
            BlockInteractionUtils.canPlaceAt(position, true)
         }
      ).filter(this::isWithinPlacementReach).toList()
   }

   private fun isSelectedFluid(fluidState: FluidState): Boolean {
      val lava: FlowingFluid = Fluids.LAVA
      if (fluidState.`is`(lava)) {
         if (this.lavaSetting.value) {
            return true
         }
      }

      val water: FlowingFluid = Fluids.WATER
      if (fluidState.`is`(water)) {
         if (this.waterSetting.value) {
            return true
         }
      }

      return false
   }

   private fun isSelectedBlockStack(stack: ItemStack): Boolean {
      if (stack.getItem() is BlockItem) {
         val blockItem = stack.getItem() as BlockItem
         val selectionMode: ListFilterMode = this.selectionSetting.value
         val block: Block = blockItem.block
         val blocks: Setting<Set<Block>> = this.blocksSetting
         return ListFilterMode.matches(selectionMode, block, blocks)
      }

      return false
   }

   private fun placeAt(
      position: BlockPos, slot: InventoryUtils.SwapSlot
   ): Boolean {
      val supportDirection: Direction? = BlockInteractionUtils.findSupportDirection(position)
      val airPlaceEnabled: Boolean = this.airPlaceSetting.value
      if (!airPlaceEnabled && supportDirection == null) {
         return false
      }

      val hand: InteractionHand = slot.hand

      val placementStack: ItemStack = slot.getStack()
      val sponge: Item = Items.SPONGE
      val isSponge: Boolean = placementStack.`is`(sponge)

      val airPlaceFlag: Boolean = this.airPlaceSetting.value
      val placed: Boolean = BlockInteractionUtils.placeBlockIgnoringCrystals(
         position, supportDirection, airPlaceFlag, hand
      )
      if (!placed) {
         // Размещение не удалось: не тратим таймер, не пишем срок губки
         // и не рисуем блок, которого не будет.
         return false
      }
      if (isSponge) {
         val spongeExpiries: MutableMap<BlockPos, Long> = this.spongeExpiryByPosition
         val expiresAt = System.currentTimeMillis() + 2500L
         spongeExpiries[position] = expiresAt
      }
      if (this.rotateSetting.value) {
         val rotationCenter: Vec3 = Vec3.atCenterOf(position)
         val rotations: FloatArray = RotationManager.getRotationsToFace(rotationCenter, supportDirection)
         val rotationManager: RotationManager = ClientContext.rotationManager
         rotationManager.requestRotation(rotations, 100)
      }

      if (this.renderSetting.value) {
         ClientContext.featureBoxRenderer.addBox(this, position)
      }

      return true
   }

   @EventHandler
   private fun onRotationRequest(event: RotationRequestEvent) {
      val timer: ElapsedTimer = this.placementTimer
      val delayMillis = this.delaySetting.value.toLong()
      if (!timer.hasElapsed(delayMillis)) {
         return
      }
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val previousSlot: Int = player.inventory.selectedSlot
      val placementPredicate = Predicate<ItemStack> { stack: ItemStack -> this.isSelectedBlockStack(stack) }
      val placementSlot: InventoryUtils.SwapSlot = InventoryUtils.findSwapSlot(placementPredicate, false)
      if (!placementSlot.isFound()) {
         return
      }
      this.spongeExpiryByPosition.entries.removeIf({ spongeEntry ->
         val currentTime = System.currentTimeMillis()
         val expiresAt: Long = spongeEntry.value
         currentTime > expiresAt
      })

      val targets: List<BlockPos> = this.collectTargetPositions()
      if (targets.isEmpty()) {
         return
      }
      var placedCount = 0
      InventoryUtils.swapTo(placementSlot)
      val targetIterator = targets.iterator()

      while (targetIterator.hasNext()) {
         val targetPosition: BlockPos = targetIterator.next()
         val placementLimit: Int = this.blocksPerTickSetting.value
         if (placedCount >= placementLimit) {
            break
         }

         if (!this.isOutsideSpongeRadius(targetPosition)) {
            val placementStack: ItemStack = placementSlot.getStack()
            val sponge: Item = Items.SPONGE
            if (placementStack.`is`(sponge)) {
               continue
            }
         }

         if (this.placeAt(targetPosition, placementSlot)) {
            val resetTimer: ElapsedTimer = this.placementTimer
            resetTimer.reset()
            placedCount++
         }
      }

      InventoryUtils.swapBack(previousSlot, placementSlot)
   }

   companion object {
      @JvmStatic
      fun bindModuleReferences(registry: ModuleRegistry) {
      }
   }

   init {
      val defaultSelection: ListFilterMode = ListFilterMode.ANY
      this.selectionSetting = this.add(EnumSetting("Selection", defaultSelection))
      val blockRegistry: Registry<Block> = BuiltInRegistries.BLOCK
      val blocksOption = RegistrySetSetting<Block>("Blocks", blockRegistry, Blocks.NETHER_PORTAL, Blocks.ENDER_CHEST, Blocks.END_PORTAL)
      this.blocksSetting = this.add(blocksOption)
      this.rangeSetting = this.add(NumberSetting<Float>("Range", 4.5f, 1.0f, 6.0f).withUnit("m"))
      this.wallRangeSetting = this.add(NumberSetting<Float>("WallRange", 4.5f, 0.0f, 6.0f).withUnit("m"))
      this.delaySetting = this.add(NumberSetting<Int>("Delay", 50, 0, 250).withUnit("ms"))
      this.blocksPerTickSetting = this.add(NumberSetting<Int>("BPT", 8, 1, 8))
      this.airPlaceSetting = this.add(BooleanSetting("AirPlace", false))
      this.rotateSetting = this.add(BooleanSetting("Rotate", true))
      this.sourcesSetting = this.add(BooleanSetting("Sources", false).markAsGroup().markVisibilityDependency())
      this.waterSetting = this.add(BooleanSetting("Water", true).dependsOn(this.sourcesSetting))
      this.lavaSetting = this.add(BooleanSetting("Lava", true).dependsOn(this.sourcesSetting))
      this.renderSetting = this.add(BooleanSetting("Render", true).markVisibilityDependency())
      val defaultFill = Color(146643455, true)
      this.fillSetting = this.add(ColorSetting("Fill", defaultFill).dependsOn(this.renderSetting))
      val defaultOutline = Color(1539152383, true)
      this.outlineSetting = this.add(ColorSetting("Outline", defaultOutline).dependsOn(this.renderSetting))
      this.lineWidthSetting = this.add(NumberSetting<Float>("LineWidth", 1.0f, 0.1f, 3.0f).dependsOn(this.renderSetting))
      this.spongeExpiryByPosition = HashMap()
      this.placementTimer = ElapsedTimer()
      val boxRenderer: FeatureBoxRenderer = ClientContext.featureBoxRenderer
      val boxBuilder: FeatureBoxRenderer.Builder = FeatureBoxRenderer.Builder(this, this.fillSetting, this.outlineSetting)
         .withLineWidth(this.lineWidthSetting)
      val boxConfiguration: FeatureBoxRenderer.RenderConfiguration = boxBuilder.build()
      boxRenderer.register(boxConfiguration)
      val blocksVisibilitySetting: Setting<Set<Block>> = this.blocksSetting
      val blocksVisibilityPredicate = Predicate<Any> { selectedBlocks: Any ->
         val selectionSetting = this.selectionSetting
         selectionSetting.value.isFiltering()
      }
      blocksVisibilitySetting.addVisibilityPredicate(blocksVisibilityPredicate)
   }
}
