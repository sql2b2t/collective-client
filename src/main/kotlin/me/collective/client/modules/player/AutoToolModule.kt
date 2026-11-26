package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.MultiPlayerGameModeStartDestroyBlockEvent
import me.collective.client.utils.world.BlockBreakingState
import me.collective.client.utils.network.PacketSender
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

class AutoToolModule : Module("AutoTool", "Equips the best tool to use on a block for you.", ModuleCategory.PLAYER) {
   @JvmField
   val blockDamageSetting: Setting<Float>
   private val inventorySetting: Setting<Boolean>
   private val swapBackSetting: Setting<Boolean>
   @JvmField
   val noBreakDelaySetting: Setting<Boolean>
   private var swapBackSlot: Int = 0
   private var swapBackPending: Boolean = false
   private var inventorySwapActive: Boolean = false

   fun isBlockDamageOverrideActive(): Boolean {
      if (!this.isToggled()) {
         return false
      }

      val blockDamage: Setting<Float> = this.blockDamageSetting
      return !blockDamage.isAtMaximum
   }

   @EventHandler
   private fun onMultiPlayerGameModeStartDestroyBlockEvent(
      event: MultiPlayerGameModeStartDestroyBlockEvent
   ) {
      if (nukerModule!!.isToggled()) {
         val nukerPos: BlockPos = event.pos
         if (nukerModule!!.canMineBlock(nukerPos)) {
            return
         }
      }

      if (!this.isBlockDamageOverrideActive()) {
         return
      }

      this.onLocalPlayerPreTickEvent(null)
      val blockPos: BlockPos = event.pos
      val direction: Direction = event.direction
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val blockState: BlockState = level.getBlockState(blockPos)
      val heldItem: ItemStack = ClientAccess.collectiveMinecraft.player!!.mainHandItem
      val onGround: Boolean = ClientAccess.collectiveMinecraft.player!!.onGround()
      val blockDamage: Double = WorldUtils.getBreakProgress(heldItem, blockState, onGround)
      val blockDamageSetting: Setting<Float> = this.blockDamageSetting
      val threshold: Float = blockDamageSetting.value
      if (blockDamage < threshold) {
         return
      }
      if (event.isCancelled) {
         return
      }

      if (!WorldUtils.canMineBlock(blockPos)) {
         return
      }

      val startAction: Action = Action.START_DESTROY_BLOCK
      PacketSender.sendPlayerAction(startAction, blockPos, direction)
      val stopAction: Action = Action.STOP_DESTROY_BLOCK
      PacketSender.sendPlayerAction(stopAction, blockPos, direction)
      if (antiCheatModule!!.is2b2tEnabled()) {
         val abortAction: Action = Action.ABORT_DESTROY_BLOCK
         PacketSender.sendPlayerAction(abortAction, blockPos, direction)
      }

      ClientContext.worldStateManager.destroyBlockPredicted(blockPos)
      event.cancel()
   }

   @EventHandler
   private fun onLocalPlayerPreTickEvent(tickEvent: LocalPlayerPreTickEvent?) {
      var breakingPos: BlockPos? = (ClientAccess.collectiveMinecraft.gameMode!! as BlockBreakingState).getCurrentBreakingBlock()
      if (tickEvent == null) {
         val hitResult: HitResult? = ClientAccess.collectiveMinecraft.hitResult
         if (hitResult is BlockHitResult) {
            breakingPos = hitResult.blockPos
         }
      }

      if (breakingPos != null && ClientAccess.collectiveMinecraft.options.keyAttack.isDown) {
         this.swapBackPending = true
         val inventorySearch: Boolean = this.inventorySetting.value
         // findBestToolSlot принимает includeInventory — раньше передавался инвертированный флаг
         // и настройка «Inventory» была мертвой (поиск шёл только по хотбару).
         val bestToolSlot: Int = InventoryUtils.findBestToolSlot(breakingPos, inventorySearch)
         if (this.inventorySetting.value) {
            if (bestToolSlot > 9 && !this.inventorySwapActive) {
               this.swapBackSlot = bestToolSlot
               InventoryUtils.swapIntoSelectedSlot(bestToolSlot)
               this.inventorySwapActive = true
               return
            }
         }

         if (!this.inventorySwapActive) {
            InventoryUtils.selectHotbarSlotForPlacement(bestToolSlot)
         }
         return
      }

      val swapBackSetting: Setting<Boolean> = this.swapBackSetting
      val swapBack: Boolean = swapBackSetting.value
      if (!swapBack) {
         return
      }
      if (this.swapBackPending) {
         this.swapBackPending = false
         if (this.inventorySwapActive) {
            val restoreSlot: Int = this.swapBackSlot
            InventoryUtils.swapIntoSelectedSlot(restoreSlot)
            this.inventorySwapActive = false
            return
         }

         val restoreInventory: Inventory = ClientAccess.collectiveMinecraft.player!!.inventory
         val restoreSlot: Int = this.swapBackSlot
         restoreInventory.setSelectedSlot(restoreSlot)
      } else if (!this.inventorySwapActive) {
         this.swapBackSlot = ClientAccess.collectiveMinecraft.player!!.inventory.getSelectedSlot()
      }
   }

   init {
      this.blockDamageSetting = this.add(NumberSetting<Float>("BlockDamage", 1.0F, 0.7F, 1.0F))
      this.inventorySetting = this.add(BooleanSetting("Inventory", false))
      this.swapBackSetting = this.add(BooleanSetting("SwapBack", false))
      this.noBreakDelaySetting = this.add(BooleanSetting("NoBreakDelay", false))
      val vanillaBlockDamageSetting: Setting<Float> = this.blockDamageSetting

      val maximumValueMarker: SettingLabelCondition = SettingLabelCondition.MAX

      vanillaBlockDamageSetting.setBoundaryLabel("Vanilla", maximumValueMarker)
   }

   companion object {
      private var antiCheatModule: AntiCheatModule? = null
      private var nukerModule: NukerModule? = null

      @JvmStatic
      fun bindModules(moduleRegistry: ModuleRegistry) {
         antiCheatModule = moduleRegistry.getModuleOrNull(AntiCheatModule::class.java)
         nukerModule = moduleRegistry.getModuleOrNull(NukerModule::class.java)
      }
   }

}
