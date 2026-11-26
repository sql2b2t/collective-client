package me.collective.client.modules.player


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.RotationRequestEvent
import me.collective.client.events.EventHandler
import me.collective.client.managers.RotationManager
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.DimensionType

import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.Plane
import net.minecraft.core.Position
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.tags.TagKey
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipBlockStateContext
import net.minecraft.world.level.block.AttachedStemBlock
import net.minecraft.world.level.block.AzaleaBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.BonemealableFeaturePlacerBlock
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.NetherFungusBlock
import net.minecraft.world.level.block.MushroomBlock
import net.minecraft.world.level.block.NetherWartBlock
import net.minecraft.world.level.block.SaplingBlock
import net.minecraft.world.level.block.StemBlock
import net.minecraft.world.level.block.SugarCaneBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

class AutoFarmModule : Module("AutoFarm", "Farms your crops for you.", ModuleCategory.PLAYER) {
   internal val modeSetting: Setting<ModeChoice>
   private val swapSetting: Setting<SwapChoice>
   val rangeSetting: Setting<Float>
   private val blocksPerTickSetting: Setting<Int>
   private val placeSetting: Setting<Boolean>
   private val rotateSetting: Setting<Boolean>
   private val fertilizeSetting: Setting<Boolean>
   internal val removeSetting: Setting<Boolean>
   private val removeDelaySetting: Setting<Float>
   private val onlyGrownSetting: Setting<Boolean>
   internal val cropsSetting: Setting<Boolean>
   private val wheatSetting: Setting<Boolean>
   private val carrotsSetting: Setting<Boolean>
   private val potatoesSetting: Setting<Boolean>
   private val beetRootsSetting: Setting<Boolean>
   private val melonsSetting: Setting<Boolean>
   private val pumpkinsSetting: Setting<Boolean>
   private val sugarCaneSetting: Setting<Boolean>
   private val netherWartSetting: Setting<Boolean>
   private val mushroomsSetting: Setting<Boolean>
   private val treesSetting: Setting<Boolean>
   private val mossSetting: Setting<Boolean>
   private val harvestTimer: ElapsedTimer
   private var plantingItem: InventoryUtils.SwapSlot? = null
   private var actionsThisTick: Int = 0

   private fun isEnabledPlantingItem(item: Item): Boolean {
      if (item == Items.MOSS_BLOCK) {
         return this.mossSetting.value
      }

      if (item is BlockItem) {
         val blockItem: BlockItem = item
         val block: Block = blockItem.block
         if (this.isTreePlant(block)) {
            return this.treesSetting.value
         }
         if (block == Blocks.MELON || this.isMelonStem(block)) {
            return this.melonsSetting.value
         }
         if (block == Blocks.PUMPKIN || this.isPumpkinStem(block)) {
            return this.pumpkinsSetting.value
         }
      }

      if (item == Items.WHEAT_SEEDS && this.wheatSetting.value) {
         return true
      }
      if (item == Items.CARROT && this.carrotsSetting.value) {
         return true
      }
      if (item == Items.POTATO && this.potatoesSetting.value) {
         return true
      }
      if (item == Items.BEETROOT_SEEDS && this.beetRootsSetting.value) {
         return true
      }
      if (item == Items.SUGAR_CANE && this.sugarCaneSetting.value) {
         return true
      }
      if (item == Items.NETHER_WART && this.netherWartSetting.value) {
         return true
      }
      if (item == Items.MELON_SEEDS && this.melonsSetting.value) {
         return true
      }
      if (item == Items.PUMPKIN_SEEDS && this.pumpkinsSetting.value) {
         return true
      }

      return this.isSupportedMushroom(item) && this.mushroomsSetting.value
   }

   private fun isPumpkinStem(block: Block): Boolean {
      return block == Blocks.PUMPKIN_STEM || block == Blocks.ATTACHED_PUMPKIN_STEM
   }

   private fun processFarmPosition(position: BlockPos, harvestFace: Direction) {
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val state: BlockState = level.getBlockState(position)
      val sugarCaneBlock = Blocks.SUGAR_CANE
      val sugarCane: Boolean = state.`is`(sugarCaneBlock)
      val plantingPredicate = Predicate<ItemStack> { stack ->
         val candidateItem: Item = stack.item

         if (!this.isEnabledPlantingItem(candidateItem)) {
            false
         } else {
            val plantingItemType: Item = stack.item
            this.canPlantAt(position, state, plantingItemType)
         }
      }
      val foundPlantingItem = InventoryUtils.findSwapSlot(plantingPredicate, false)
      this.plantingItem = foundPlantingItem
      var hasPlantingItem: Boolean = foundPlantingItem.isFound()
      if (this.swapSetting.value == SwapChoice.NONE) {
         val heldItem: InventoryUtils.SwapSlot = foundPlantingItem
         if (!heldItem.isInHand()) {
            hasPlantingItem = false
            this.plantingItem = null
         }
      }

      if ((this.placeSetting.value) && hasPlantingItem) {
         val itemToPlant: InventoryUtils.SwapSlot = this.plantingItem!!
         this.plantAt(position, itemToPlant)
         return
      }
      var itemType: Item = state.block.asItem()

      if (this.isEnabledPlantingItem(itemType)) {
         val readyToHarvest: Boolean = this.isReadyToHarvest(state, position)
         itemType = Items.BONE_MEAL

         val boneMealHand = this.findItemHand(itemType)
         if ((this.fertilizeSetting.value) && !readyToHarvest && boneMealHand != null && !sugarCane) {
            this.fertilizeAt(position, boneMealHand)
         } else {
            this.harvestAt(position, harvestFace, readyToHarvest)
         }
      }
   }

   private fun findHarvestFace(position: BlockPos): Direction {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val eyePosition: Vec3 = player.getEyePosition()
      val clipContext = ClipBlockStateContext(eyePosition, Vec3.atCenterOf(position), Predicate { state -> true })
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val hitFace: Direction = level.isBlockInLine(clipContext).getDirection()

      val validFaces: List<Direction> = BlockInteractionUtils.getStrictDirections(position)
      if (!validFaces.contains(hitFace) && !validFaces.isEmpty()) {
         return validFaces.first()
      }
      return hitFace
   }

   private fun findItemHand(item: Item): InteractionHand? {
      if (ClientAccess.collectiveMinecraft.player!!.getMainHandItem().`is`(item)) {
         return InteractionHand.MAIN_HAND
      }

      return if (ClientAccess.collectiveMinecraft.player!!.getOffhandItem().`is`(item)) InteractionHand.OFF_HAND else null
   }

   private fun isMelonStem(block: Block): Boolean {
      return block == Blocks.MELON_STEM || block == Blocks.ATTACHED_MELON_STEM
   }

   private fun isSupportedMushroom(item: Item): Boolean {
      if (item != Items.RED_MUSHROOM && item != Items.BROWN_MUSHROOM) {
         return item == Items.CRIMSON_FUNGUS || item == Items.WARPED_FUNGUS
      }

      val dimension: DimensionType = ClientContext.worldStateManager.getDimension()
      return !dimension.isNether()
   }

   private fun isTreePlant(block: Block): Boolean {
      return block is SaplingBlock || block is AzaleaBlock
   }

   private fun canPlantAt(
      position: BlockPos, initialState: BlockState, item: Item
   ): Boolean {
      var state: BlockState = initialState
      var needsFarmland: Boolean = item == Items.MELON_SEEDS || item == Items.PUMPKIN_SEEDS
      if (item is BlockItem) {
         val blockItem: BlockItem = item
         val block: Block = blockItem.block

         if (this.isTreePlant(block)) {
            val dirtTag: TagKey<Block> = BlockTags.DIRT
            if (!state.`is`(dirtTag)) {
               return false
            }
            val plantBounds = AABB(position.above())
            val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val playerBounds: AABB = player.getBoundingBox()
            if (plantBounds.intersects(playerBounds)) {
               return false
            }

            val supportDirtTag: TagKey<Block> = BlockTags.DIRT
            if (state.`is`(supportDirtTag)) {
               val treeLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
               val treePlantPosition: BlockPos = position.above()
               val treePlantState: BlockState = treeLevel.getBlockState(treePlantPosition)
               val treeAirBlock = Blocks.AIR
               if (treePlantState.`is`(treeAirBlock)) {
                  return true
               }
            }

            return false
         }

         if (block is NetherWartBlock) {
            val soulSand = Blocks.SOUL_SAND
            if (state.`is`(soulSand)) {
               val wartLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
               val wartPlantPosition: BlockPos = position.above()
               val wartPlantState: BlockState = wartLevel.getBlockState(wartPlantPosition)
               val wartAirBlock = Blocks.AIR
               if (wartPlantState.`is`(wartAirBlock)) {
                  return true
               }
            }

            return false
         }

         if (block is MushroomBlock) {
            val mushroomLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
            val mushroomPlantState: BlockState = mushroomLevel.getBlockState(position.above())
            if (!mushroomPlantState.`is`(Blocks.AIR)) {
               return false
            }
            // Грибы сажаются на любую твёрдую верхнюю грань
            return state.isFaceSturdy(mushroomLevel, position, Direction.UP)
         }

         if (block is NetherFungusBlock) {
            val nyliumTag: TagKey<Block> = BlockTags.NYLIUM
            if (!state.`is`(nyliumTag)) {
               return false
            }
            val fungusLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
            val fungusPlantState: BlockState = fungusLevel.getBlockState(position.above())
            return fungusPlantState.`is`(Blocks.AIR)
         }

         if (block == Blocks.MOSS_BLOCK) {
            val mossLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
            val mossPlantState: BlockState = mossLevel.getBlockState(position.above())
            if (!mossPlantState.`is`(Blocks.AIR)) {
               return false
            }
            return state.isFaceSturdy(mossLevel, position, Direction.UP)
         }

         if (block is CropBlock) {
            needsFarmland = true
         }

         if (block is SugarCaneBlock) {
            var sugarCaneLevelSnapshot: ClientLevel = ClientAccess.collectiveMinecraft.level!!
            val canePlantPosition: BlockPos = position.above()
            val canePlantState: BlockState = sugarCaneLevelSnapshot.getBlockState(canePlantPosition)
            val caneAirBlock = Blocks.AIR
            if (!canePlantState.`is`(caneAirBlock)) {
               return false
            }

            val caneDirtTag: TagKey<Block> = BlockTags.DIRT
            if (!state.`is`(caneDirtTag)) {
               val caneSandTag: TagKey<Block> = BlockTags.SAND
               if (!state.`is`(caneSandTag)) {
                  return false
               }
            }

            val horizontalPlane: Plane = Plane.HORIZONTAL
            for (direction in horizontalPlane) {
               sugarCaneLevelSnapshot = ClientAccess.collectiveMinecraft.level!!
               val neighborFluidPosition: BlockPos = position.relative(direction)
               val neighborFluid: FluidState = sugarCaneLevelSnapshot.getFluidState(neighborFluidPosition)
               sugarCaneLevelSnapshot = ClientAccess.collectiveMinecraft.level!!
               val neighborBlockPosition: BlockPos = position.relative(direction)
               state = sugarCaneLevelSnapshot.getBlockState(neighborBlockPosition)
               val waterTag = FluidTags.WATER
               if (neighborFluid.`is`(waterTag)) {
                  return true
               }

               val frostedIce = Blocks.FROSTED_ICE
               if (state.`is`(frostedIce)) {
                  return true
               }
            }
         }
      }

      if (!needsFarmland) {
         return false
      }

      val farmland = Blocks.FARMLAND
      if (state.`is`(farmland)) {
         val cropLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         val cropPlantPosition: BlockPos = position.above()
         val cropPlantState: BlockState = cropLevel.getBlockState(cropPlantPosition)
         val cropAirBlock = Blocks.AIR
         if (cropPlantState.`is`(cropAirBlock)) {
            return true
         }
      }

      return false
   }

   private fun isReadyToHarvest(state: BlockState, position: BlockPos): Boolean {
      val block: Block = state.block
      if (block == Blocks.MOSS_BLOCK) {
         val abovePosition: BlockPos = position.above()
         return !WorldUtils.isAir(abovePosition)
      }

      if (this.isTreePlant(block)) {
         return false
      } else if (block is CropBlock) {
         val crop: CropBlock = block
         return crop.getAge(state) == crop.getMaxAge()
      } else if (block is NetherWartBlock) {
         val wartAgeProperty: IntegerProperty = NetherWartBlock.AGE

         return state.getValue(wartAgeProperty) == (3)
      } else if (block is SugarCaneBlock) {
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         val belowPosition: BlockPos = position.below()
         val belowState: BlockState = level.getBlockState(belowPosition)
         val sugarCaneBlock = Blocks.SUGAR_CANE
         return belowState.`is`(sugarCaneBlock)
      } else if (block == Blocks.PUMPKIN || block == Blocks.MELON) {
         return true
      } else if (block is StemBlock) {
         val stemAgeProperty: IntegerProperty = StemBlock.AGE

         return state.getValue(stemAgeProperty) == (7)
      } else {
         return block is AttachedStemBlock
      }
   }

   private fun plantAt(
      position: BlockPos, plantingItem: InventoryUtils.SwapSlot
   ) {
      InventoryUtils.swapTo(plantingItem)
      val hitResult = BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false)
      val gameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val hand: InteractionHand = plantingItem.hand
      gameMode.useItemOn(player, hand, hitResult)
      val swingPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val swingHand: InteractionHand = plantingItem.hand
      swingPlayer.swing(swingHand)

      this.actionsThisTick++
      if (this.rotateSetting.value) {
         val rotationManager: RotationManager = ClientContext.rotationManager
         val targetCenter: Vec3 = Vec3.atCenterOf(position)

         val targetRotation = RotationManager.getRotationsToPosition(targetCenter)
         val rotationPriority: Byte = 0
         rotationManager.requestRotation(targetRotation, rotationPriority.toInt())
      }
   }

   private fun fertilizeAt(position: BlockPos, hand: InteractionHand) {
      if (this.rotateSetting.value) {
         val rotationManager: RotationManager = ClientContext.rotationManager
         val targetBottomCenter: Vec3 = Vec3.atBottomCenterOf(position)

         val targetRotation = RotationManager.getRotationsToPosition(targetBottomCenter)
         val rotationPriority: Byte = 0
         rotationManager.requestRotation(targetRotation, rotationPriority.toInt())
      }

      val gameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val hitResult = BlockHitResult(Vec3.atBottomCenterOf(position), Direction.UP, position, false)
      gameMode.useItemOn(player, hand, hitResult)
      ClientAccess.collectiveMinecraft.player!!.swing(hand)

      this.actionsThisTick++
   }

   @EventHandler
   private fun updateFarm(event: RotationRequestEvent) {
      this.plantingItem = null

      this.runFarmTick()
   }

   private fun runFarmTick() {
      val originalSlot: Int = InventoryUtils.getSelectedSlot()
      val targetPositions: List<BlockPos> = (this.modeSetting.value).getTargets(this)
      for (position in targetPositions) {
         val actionCount: Int = this.actionsThisTick
         val blocksPerTickValue: Int = this.blocksPerTickSetting.value
         if (actionCount >= blocksPerTickValue) {
            break
         }

         val harvestFace: Direction = this.findHarvestFace(position)

         this.processFarmPosition(position, harvestFace)
      }

      this.actionsThisTick = 0
      val selectedPlantingItem = this.plantingItem
      if (selectedPlantingItem != null) {
         if (this.swapSetting.value == SwapChoice.SILENT) {
            InventoryUtils.swapBack(originalSlot, selectedPlantingItem)
         }
      }
   }

   private fun harvestAt(
      position: BlockPos, face: Direction, readyToHarvest: Boolean
   ) {
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val block: Block = level.getBlockState(position).block
      if (block is SaplingBlock) {
         return
      }
      if (block is BonemealableFeaturePlacerBlock) {
         return
      }
      if (block == Blocks.PUMPKIN_STEM || block == Blocks.MELON_STEM) {
         return
      }
      val removeValue: Boolean = this.removeSetting.value
      if (!removeValue) {
         return
      }
      var timer: ElapsedTimer = this.harvestTimer
      val delaySeconds: Double = (this.removeDelaySetting.value).toDouble()
      val delayUnit: TimeUnit = TimeUnit.SECONDS
      if (!timer.hasElapsed(delaySeconds, delayUnit)) {
         return
      }
      if (!readyToHarvest) {
         if (this.onlyGrownSetting.value) {
            return
         }
      }
      val gameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
      gameMode.continueDestroyBlock(position, face)
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val hand: InteractionHand = InteractionHand.MAIN_HAND
      player.swing(hand)
      this.actionsThisTick++
      timer = this.harvestTimer

      timer.reset()
      if (this.rotateSetting.value) {
         val rotationManager: RotationManager = ClientContext.rotationManager
         val targetCenter: Vec3 = Vec3.atCenterOf(position)

         val targetRotation = RotationManager.getRotationsToPosition(
            targetCenter
         )
         val rotationPriority: Byte = 0
         rotationManager.requestRotation(targetRotation, rotationPriority.toInt())
      }
   }

   init {
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", ModeChoice.CROSSHAIR))
      this.swapSetting = this.add(EnumSetting<SwapChoice>("Swap", SwapChoice.NORMAL))
      val rangeVisibility = Predicate<Float> { ignoredValue ->
         val setting: Setting<ModeChoice> = this.modeSetting
         setting.value == ModeChoice.SPHERE
      }
      this.rangeSetting = this.add(NumberSetting<Float>("Range", 4.0F, 1.0F, 6.0F, rangeVisibility).withUnit("m"))
      this.blocksPerTickSetting = this.add(NumberSetting<Int>("Frequency", 1, 1, 8))
      val defaultPlace: Boolean = false
      this.placeSetting = this.add(BooleanSetting("Place", defaultPlace))
      val defaultRotate: Boolean = false
      this.rotateSetting = this.add(BooleanSetting("Rotate", defaultRotate))
      val defaultFertilize: Boolean = false
      this.fertilizeSetting = this.add(BooleanSetting("Fertilize", defaultFertilize))
      val defaultRemove: Boolean = false
      this.removeSetting = this.add(BooleanSetting("Remove", defaultRemove).markVisibilityDependency())
      val removeDelayVisibility = Predicate<Float> { ignoredValue ->
         val setting: Setting<Boolean> = this.removeSetting
         setting.isExpanded()
      }
      this.removeDelaySetting = this.add(NumberSetting<Float>("RemoveDelay", 0.5F, 0.0F, 5.0F, removeDelayVisibility).withUnit("s"))
      val onlyGrownVisibility = Predicate<Boolean> { ignoredValue ->
         val setting: Setting<Boolean> = this.removeSetting
         setting.isExpanded()
      }
      val defaultOnlyGrown: Boolean = true
      this.onlyGrownSetting = this.add(BooleanSetting("OnlyGrown", defaultOnlyGrown, onlyGrownVisibility))
      val defaultCrops: Boolean = false
      val registeredCrops: Setting<Boolean> = this.add(BooleanSetting("Crops", defaultCrops).markAsGroup().markVisibilityDependency())
      this.cropsSetting = registeredCrops
      val wheatVisibility = Predicate<Boolean> { ignoredValue ->
         val setting: Setting<Boolean> = this.cropsSetting
         setting.isExpanded()
      }
      val defaultWheat: Boolean = true
      this.wheatSetting = this.add(BooleanSetting("Wheat", defaultWheat, wheatVisibility))
      val carrotsVisibility = Predicate<Boolean> { ignoredValue ->
         val setting: Setting<Boolean> = this.cropsSetting
         setting.isExpanded()
      }
      val defaultCarrots: Boolean = true
      this.carrotsSetting = this.add(BooleanSetting("Carrots", defaultCarrots, carrotsVisibility))
      val potatoesVisibility = Predicate<Boolean> { ignoredValue ->
         val setting: Setting<Boolean> = this.cropsSetting
         setting.isExpanded()
      }
      val defaultPotatoes: Boolean = true
      this.potatoesSetting = this.add(BooleanSetting("Potatoes", defaultPotatoes, potatoesVisibility))
      val beetRootsVisibility = Predicate<Boolean> { ignoredValue ->
         val setting: Setting<Boolean> = this.cropsSetting
         setting.isExpanded()
      }
      val defaultBeetRoots: Boolean = false
      this.beetRootsSetting = this.add(BooleanSetting("BeetRoots", defaultBeetRoots, beetRootsVisibility))
      val defaultMelons: Boolean = false
      this.melonsSetting = this.add(BooleanSetting("Melons", defaultMelons).dependsOn(registeredCrops))
      val defaultPumpkins: Boolean = false
      this.pumpkinsSetting = this.add(BooleanSetting("Pumpkins", defaultPumpkins).dependsOn(registeredCrops))
      val defaultSugarCane: Boolean = false
      this.sugarCaneSetting = this.add(BooleanSetting("SugarCane", defaultSugarCane).dependsOn(registeredCrops))
      val defaultNetherWart: Boolean = false
      this.netherWartSetting = this.add(BooleanSetting("NetherWart", defaultNetherWart).dependsOn(registeredCrops))
      val defaultMushrooms: Boolean = false
      this.mushroomsSetting = this.add(BooleanSetting("Mushrooms", defaultMushrooms).dependsOn(registeredCrops))
      val defaultTrees: Boolean = false
      this.treesSetting = this.add(BooleanSetting("Trees", defaultTrees).dependsOn(registeredCrops))
      val defaultMoss: Boolean = false
      this.mossSetting = this.add(BooleanSetting("Moss", defaultMoss).dependsOn(registeredCrops))
      this.harvestTimer = ElapsedTimer()
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      CROSSHAIR("Crosshair") {
         override fun getTargets(module: AutoFarmModule): List<BlockPos> {
            val hitResult: HitResult = ClientAccess.collectiveMinecraft.hitResult ?: return Collections.emptyList()
            val hitLocation: Vec3 = hitResult.getLocation()
            val targetPosition: BlockPos = BlockPos.containing(hitLocation as Position)
            return Collections.singletonList(targetPosition)
         }
      },
      SPHERE("Sphere") {
         override fun getTargets(module: AutoFarmModule): List<BlockPos> {
            val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val playerPosition: Vec3 = player.position()
            val rangeSnapshot: Float = module.rangeSetting.value
            return WorldUtils.collectSphereBlocks(playerPosition, rangeSnapshot, true)
         }
      };

      override fun getName(): String {
         return this.displayName
      }

      abstract fun getTargets(module: AutoFarmModule): List<BlockPos>
   }

   enum class SwapChoice(private val displayName: String) : NamedEntry {
      NONE("None"),
      NORMAL("Normal"),
      SILENT("Silent");

      override fun getName(): String {
         return this.displayName
      }
   }
}
