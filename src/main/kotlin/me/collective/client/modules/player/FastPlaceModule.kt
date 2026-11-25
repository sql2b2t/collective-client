package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.events.RotationRequestEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import me.collective.client.managers.SwingInteractionManager
import me.collective.client.managers.RotationManager
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.exploit.MultiTaskModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import me.collective.mixin.MinecraftAccessor
import java.util.Random
import java.util.function.Predicate
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ExperienceBottleItem
import net.minecraft.world.item.FireworkRocketItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.SignItem
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

class FastPlaceModule : Module("FastPlace", "Removes the right-click delay for some items.", ModuleCategory.PLAYER) {

   companion object {
      private var speedMine: SpeedMineModule? = null
      private var multiTask: MultiTaskModule? = null

      @JvmStatic
      fun bindModules(registry: ModuleRegistry) {
         speedMine = registry.getModule(
            SpeedMineModule::class.java
         ) as SpeedMineModule?
         multiTask = registry.getModule(
            MultiTaskModule::class.java
         ) as MultiTaskModule?
      }
   }

   @JvmField
   val delaySetting: Setting<Int>
   private val autoPlaceSetting: Setting<Boolean>
   private val randomBlockSetting: Setting<Boolean>
   private val itemsSetting: Setting<Boolean>
   @JvmField
   val allSetting: Setting<Boolean>
   private val expSetting: Setting<Boolean>
   private val rocketsSetting: Setting<Boolean>
   @JvmField
   val blocksSetting: Setting<Boolean>
   private val foodSetting: Setting<Boolean>
   private val toolsSetting: Setting<Boolean>
   private val enderChestsSetting: Setting<Boolean>
   private val autoReplaceSetting: Setting<Boolean>
   private val random: Random
   private var redirectingEnderChestUse: Boolean = false

   @EventHandler
   private fun onAutoPlaceTick(event: LocalPlayerPreTickEvent) {
      val accessor = ClientAccess.collectiveMinecraft as MinecraftAccessor
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val foodStack: ItemStack = player.getMainHandItem()
      val foodComponent: DataComponentType<*> = DataComponents.FOOD
      if (!foodStack.has(foodComponent)) {
         if (this.autoPlaceSetting.value && accessor.getItemUseCooldown() == 0) {
            accessor.interact()
         }
      }
   }

   @EventHandler
   private fun onDelayTick(event: LocalPlayerTickEvent) {
      if (!this.isWorldUnavailable()) {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val stack: ItemStack = player.getMainHandItem()
         val item: Item = stack.getItem()
         if (this.shouldUseFastDelay(stack, item)) {
            val accessor = ClientAccess.collectiveMinecraft as MinecraftAccessor
            val cooldown: Int = (ClientAccess.collectiveMinecraft as MinecraftAccessor)
               .getItemUseCooldown()
            val delayTicks: Int = this.delaySetting.value
            val currentCooldown: Int = cooldown
            accessor.setItemUseCooldown(Math.min(currentCooldown, delayTicks))
         }
      }
   }

   @EventHandler
   private fun onSendPacket(event: PacketEvent.Send) {
      val useItemPacket = event.packet
      if (useItemPacket is ServerboundUseItemOnPacket) {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val hand: InteractionHand = useItemPacket.getHand()
         if (player.getItemInHand(hand).getItem() is BlockItem) {
            val eventLoop = ClientAccess.collectiveMinecraft
            val task: Runnable = Runnable { this.selectRandomBlock() }
            eventLoop.execute(task)
         }
      }
   }

   private fun isRightClickTool(stack: ItemStack): Boolean {
      val axes = ItemTags.AXES
      if (stack.`is`(axes)) {
         return true
      }
      val shovels = ItemTags.SHOVELS
      if (stack.`is`(shovels)) {
         return true
      }
      val hoes = ItemTags.HOES
      return stack.`is`(hoes)
   }

   private fun hideItemSettingsWhenAllEnabled(vararg settings: Setting<Boolean>) {
      for (setting in settings) {
         val visibility: Predicate<Any> = Predicate { ignored ->
            val allEnabled: Boolean = this.allSetting.value
            !allEnabled
         }

         setting.addVisibilityPredicate(visibility)
      }
   }

   @EventHandler
   private fun onUseItemOn(event: MultiPlayerGameModeUseItemOnEvent) {
      if (!this.redirectingEnderChestUse) {
         val playerForSneaking: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         if (!playerForSneaking.isShiftKeyDown()) {
            val hit: BlockHitResult = event.hitResult
            val enderChestsEnabled: Boolean = this.enderChestsSetting.value
            if (!enderChestsEnabled) {
               val allEnabled: Boolean = this.allSetting.value
               if (!allEnabled) {
                  return
               }
            }

            val playerForHeldItem: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val enderChestItem: Item = Items.ENDER_CHEST
            if (!playerForHeldItem.isHolding(enderChestItem)) {
               return
            }

            val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
            val statePosition: BlockPos = hit.getBlockPos()
            val blockState: BlockState = level.getBlockState(statePosition)
            val enderChestBlock: Block = Blocks.ENDER_CHEST
            val targetState: BlockState = blockState
            if (!targetState.`is`(enderChestBlock)) {
               return
            }

            val hitBlockPosition: BlockPos = hit.getBlockPos()

            // Размещаем в клетку РЯДОМ с кликнутым сундуком (по нормали клика):
            // раньше цель считалась как supportPosition.relative(face) = сама
            // клетка сундука (занята), и размещение проваливалось.
            val strictDirection: Boolean = BlockInteractionUtils.isStrictDirectionEnabled()
            val targetPosition: BlockPos = hitBlockPosition.relative(hit.getDirection())
            val supportDirection: Direction? = BlockInteractionUtils.findSupportDirection(targetPosition, strictDirection)
            if (supportDirection == null) {
               return
            }

            val relativeOrigin: BlockPos = targetPosition
            val directionForRelativePosition: Direction = supportDirection
            val supportPosition: BlockPos = relativeOrigin.relative(directionForRelativePosition)
            val center: Vec3 = supportPosition.getCenter()
            val opposite: Direction = supportDirection.getOpposite()

            val halfBlock: Double = 0.5
            val oppositeDirection: Direction = opposite
            val hitPosition: Vec3 = center.relative(oppositeDirection, halfBlock)
            val directionForRedirectedHit: Direction = supportDirection
            val redirectedHit = BlockHitResult(hitPosition, directionForRedirectedHit.getOpposite(), supportPosition, false)
            val offhand: ItemStack = ClientAccess.collectiveMinecraft.player!!.getOffhandItem()
            val offhandEnderChest: Item = Items.ENDER_CHEST
            val hand: InteractionHand = if (offhand.`is`(offhandEnderChest)) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND

            if (RotationManager.isMovementSyncEnabled()) {
               if (RotationManager.isSilentRotationMode()) {
                  val rotationTarget: Vec3 = hitPosition
                  val rotations: FloatArray = RotationManager.getRotationsToPosition(
                     rotationTarget
                  )
                  val tickCount: Int = ClientAccess.collectiveMinecraft.player!!.tickCount

                  val yawOffset: Float = (
                        (tickCount % (6)) - (3)
                     ) *
         AntiCheatModule.ROTATION_JITTER_STEP
                  val playerX: Double = ClientAccess.collectiveMinecraft.player!!.getX()
                  val playerY: Double = ClientAccess.collectiveMinecraft.player!!.getY()
                  val playerZ: Double = ClientAccess.collectiveMinecraft.player!!.getZ()
                  val yaw: Float = rotations[0] + yawOffset
                  val pitch: Float = rotations[1]
                  val packetOnGround: Boolean = ClientContext.playerStateTracker.isPacketOnGround()
                  PacketSender.sendPositionRotation(playerX, playerY, playerZ, yaw, pitch, packetOnGround)
               }
            }

            this.redirectingEnderChestUse = true
            val gameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
            val playerForUse: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            gameMode.useItemOn(playerForUse, hand, redirectedHit)
            event.cancel()
            this.redirectingEnderChestUse = false
            return
         }
      }
   }

   private fun shouldUseFastDelay(stack: ItemStack, item: Item): Boolean {
      if (item is SignItem) {
         return false
      }

      if (this.allSetting.value) {
         return true
      }

      if (item is ExperienceBottleItem) {
         if (this.expSetting.value) {
            return true
         }
      }

      if (item is FireworkRocketItem) {
         if (this.rocketsSetting.value) {
            return true
         }
      }

      if (item is BlockItem) {
         if (this.blocksSetting.value) {
            return true
         }
      }

      if (item === Items.ENDER_CHEST) {
         if (this.enderChestsSetting.value) {
            return true
         }
      }

      if (this.isRightClickTool(stack)) {
         if (this.toolsSetting.value) {
            return true
         }
      }

      val consumableComponent: DataComponentType<*> = DataComponents.CONSUMABLE
      if (stack.has(consumableComponent)) {
         if (this.foodSetting.value) {
            return true
         }
      }

      return false
   }

   @EventHandler
   private fun onRotationRequest(event: RotationRequestEvent) {
      if (this.autoReplaceSetting.value) {
         if (this.enderChestsSetting.value) {
            val miningPosition: BlockPos? = speedMine!!.miningPos

            val miningReady: Boolean = speedMine!!.getRenderProgress() >= 1.0F
            if (miningPosition == null) {
               return
            }

            val positionForReplaceable: BlockPos = miningPosition
            if (!WorldUtils.isReplaceable(positionForReplaceable)) {
               val positionForBlock: BlockPos = miningPosition
               if (WorldUtils.getBlock(positionForBlock) !== Blocks.ENDER_CHEST ||
         !miningReady) {
                  return
               }
            }

            val positionForBounds: BlockPos = miningPosition
            if (BlockInteractionUtils.isOutsidePlacementBounds(positionForBounds)) {
               return
            }

            val collisionBlock: Block = Blocks.ENDER_CHEST
            val positionForCollision: BlockPos = miningPosition
            val bounds: AABB = BoundingBoxUtils.getBlockCollisionBounds(positionForCollision, collisionBlock)
            val ignoreCrystals: Boolean = true
            val playersOnly: Boolean = false
            val collisionBounds: AABB = bounds
            if (BlockInteractionUtils.hasBlockingEntity(collisionBounds, playersOnly, ignoreCrystals)) {
               return
            }

            if (multiTask!!.isAutoReplaceDisabled()) {
               if (multiTask!!.isItemUseRestricted()) {
                  return
               }
            }

            val previousSlot: Int = InventoryUtils.getSelectedSlot()

            val enderChestItem: Item = Items.ENDER_CHEST
            val enderChestSlot: Int = InventoryUtils.findHotbarSlot(enderChestItem)

            val strictDirection: Boolean = BlockInteractionUtils.isStrictDirectionEnabled()
            val positionForSupport: BlockPos = miningPosition
            val supportDirection: Direction? = BlockInteractionUtils.findSupportDirection(positionForSupport, strictDirection)
            if (supportDirection != null && enderChestSlot != -1) {
               val slotToSelect: Int = enderChestSlot
               InventoryUtils.selectHotbarSlotForPlacement(slotToSelect)
               val mainHand: InteractionHand = InteractionHand.MAIN_HAND

               BlockInteractionUtils.sendPlacement(miningPosition, null, supportDirection, false, mainHand)
               val swingAction: SwingAction = SwingAction.INTERACT
               val swingHand: InteractionHand = InteractionHand.MAIN_HAND
               val swingManager: SwingInteractionManager = ClientContext.swingManager
               swingManager.swingPacket(swingHand, swingAction)

               val slotToRestore: Int = previousSlot
               InventoryUtils.selectHotbarSlotForPlacement(slotToRestore)
               val managerForRequest = ClientContext.rotationManager
               val miningCenter: Vec3 = miningPosition.getCenter()

               val halfBlock: Double = 0.5
               val directionForOffset: Direction = supportDirection

               val rotationTarget: Vec3 = miningCenter.relative(directionForOffset, halfBlock)
               val rotations: FloatArray = RotationManager.getRotationsToPosition(
                  rotationTarget
               )

               managerForRequest.requestRotation(rotations, 200)
               return
            }

            return
         }
      }
   }

   private fun selectRandomBlock() {
      if (this.randomBlockSetting.value) {
         val blockSlots = IntArray(9)
         var blockCount: Int = 0
         var slot: Int = 0

         while (true) {
            if (slot >= (9)) {
               if (blockCount < (2)) {
                  return
               }

               val randomBound: Int = blockCount
               val randomSource: Random = this.random
               val selectedSlot: Int = blockSlots[randomSource.nextInt(randomBound)]

               InventoryUtils.selectHotbarSlot(selectedSlot)
               return
            }

            val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val inventory: Inventory = player.getInventory()
            val slotForItem: Int = slot
            val stack: ItemStack = inventory.getItem(slotForItem)
            val stackForEmptyCheck: ItemStack = stack
            if (!stackForEmptyCheck.isEmpty()) {
               if (stack.getItem() is BlockItem) {
                  blockSlots[blockCount++] = slot
               }
            }

            slot++
         }
      }
   }

   init {
      this.delaySetting = this.add(NumberSetting<Int>("Delay", 2, 0, 4))
      this.autoPlaceSetting = this.add(BooleanSetting("AutoPlace", false))
      this.randomBlockSetting = this.add(BooleanSetting("RandomBlock", false))
      val items = BooleanSetting("Items", false)
      items.markAsGroup()
      items.markVisibilityDependency()
      this.itemsSetting = this.add(items)
      this.allSetting = this.add(BooleanSetting("All", false).dependsOn(this.itemsSetting))
      this.expSetting = this.add(BooleanSetting("Exp", true).dependsOn(this.itemsSetting))
      this.rocketsSetting = this.add(BooleanSetting("Rockets", false).dependsOn(this.itemsSetting))
      this.blocksSetting = this.add(BooleanSetting("Blocks", false).dependsOn(this.itemsSetting))
      this.foodSetting = this.add(BooleanSetting("Food", true).dependsOn(this.itemsSetting))
      this.toolsSetting = this.add(BooleanSetting("Tools", false).dependsOn(this.itemsSetting))
      val enderChests = BooleanSetting("EnderChests", true)
      enderChests.markVisibilityDependency()
      this.enderChestsSetting = this.add(enderChests.dependsOn(this.itemsSetting))
      this.autoReplaceSetting = this.add(BooleanSetting("AutoReplace", false)
         .dependsOn(this.itemsSetting, this.enderChestsSetting))
      this.random = Random()
      this.redirectingEnderChestUse = false

      val itemSettings = arrayOf<Setting<Boolean>>(
         this.expSetting,
         this.rocketsSetting,
         this.blocksSetting,
         this.enderChestsSetting,
         this.foodSetting,
         this.toolsSetting
      )
      this.hideItemSettingsWhenAllEnabled(*itemSettings)
      this.setDrawn(false)
   }
}
