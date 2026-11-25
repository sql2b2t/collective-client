package me.collective.client.utils.world

import me.collective.client.managers.ServerStateManager
import me.collective.client.managers.WorldStateManager
import me.collective.client.utils.inventory.EnchantmentUtils

import me.collective.client.utils.ElapsedTimer
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.events.ConnectScreenConnectEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.bus.EventBus
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.settings.Setting
import me.collective.client.settings.options.SpeedMineRebreakChoice

import java.util.ArrayList
import java.util.Collections
import java.util.Iterator
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.state.BlockState

class BlockBreakTracker : ClientAccess {
   val entries: MutableList<BlockBreakEntry> = Collections.synchronizedList(ArrayList())

   init {
      val eventBus: EventBus = ClientAccess.eventBus
      eventBus.subscribe(this)
   }

   private fun onBlockRemoved(position: BlockPos) {
      ClientAccess.collectiveMinecraft.execute {
         val iterator = entries.iterator()

         while (iterator.hasNext()) {
            val entry = iterator.next()

            if (position == entry.getPosition()) {
               val rebreakSetting: Setting<SpeedMineRebreakChoice> = speedMine!!.rebreakSetting
               if (rebreakSetting.getValue() != SpeedMineRebreakChoice.INSTANT) {
                  entry.setProgress(0.0f)
                  entry.setAirTicks(0)
               }

               entry.markBroken()

               val progressTimer: ElapsedTimer = entry.getProgressTimer()

               progressTimer.reset()
            }
         }
      }
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      entries.removeIf(BlockBreakEntry::isExpired)
      val iterator = entries.iterator()

      while (iterator.hasNext()) {
         val entry = iterator.next()
         val levelSnapshot = ClientAccess.collectiveMinecraft.level ?: return

         val statePosition = entry.getPosition()

         val blockState: BlockState = levelSnapshot.getBlockState(statePosition)

         val pickaxeSnapshot = getMiningPickaxe()
         val baseProgress = WorldUtils.getBreakProgress(pickaxeSnapshot, blockState, true).toFloat()
         val serverStateSnapshot: ServerStateManager = ClientContext.serverStateManager

         var progressDelta = baseProgress * serverStateSnapshot.tickRateScale
         val speedMineSnapshot = speedMine!!
         if (speedMineSnapshot.isToggled() && !entry.isExtraBreak()) {
            val damageSetting: Setting<Float> = speedMineSnapshot.damageSetting
            val damageScale: Float = damageSetting.getValue()
            progressDelta /= damageScale
         }

         val miningPositionSnapshot = entry.getPosition()

         if (WorldUtils.canMineBlock(miningPositionSnapshot)) {
            val airTicksSnapshot = entry.getAirTicks()
            progressDelta *= Math.max(airTicksSnapshot, 1).toFloat()
            entry.setAirTicks(0)
         } else {
            progressDelta = 0.0f

            val nextAirTicks = entry.getAirTicks() + 1

            entry.setAirTicks(nextAirTicks)

            entry.resetInactivityTimer()
         }

         val previousProgress = entry.getProgress()
         val nextProgress = previousProgress + progressDelta

         entry.setProgress(nextProgress)
         if (Math.min(nextProgress.toDouble(), 1.0) > previousProgress) {
            val progressTimerSnapshot: ElapsedTimer = entry.getProgressTimer()

            progressTimerSnapshot.reset()
         }

         if (!(nextProgress >= 0.8)) {
            continue
         }
         if (entry.isBroken()) {
            continue
         }
         val rollbackTimerSnapshot: ElapsedTimer = entry.getProgressTimer()

         if (rollbackTimerSnapshot.hasElapsed(150L)) {
            continue
         }
         val worldStateSnapshot: WorldStateManager = ClientContext.worldStateManager

         val rollbackPositionSnapshot = entry.getPosition()

         worldStateSnapshot.saveBlockForRollback(rollbackPositionSnapshot)
      }
   }

   private fun trackBlockBreaking(position: BlockPos, entityId: Int) {
      var alreadyTracked = false
      var hasExtraBreak = false
      val iterator = entries.iterator()

      while (iterator.hasNext()) {
         val entry = iterator.next()

         if (entry.getEntityId() == entityId) {
            if (entry.getPosition() == position) {
               alreadyTracked = true
            }

            if (entry.isExtraBreak()) {
               hasExtraBreak = true
            }
         }
      }

      if (alreadyTracked) {
         return
      }
      var extraBreak = false
      if (!hasExtraBreak) {
         val extraBreakSetting: Setting<Boolean> = speedMine!!.extraBreakSetting
         val extraBreakEnabled: Boolean = extraBreakSetting.getValue()
         extraBreak = extraBreakEnabled
      }

      entries.removeIf { existingEntry ->
         if (existingEntry.isExtraBreak()) {
            return@removeIf false
         }
         existingEntry.getPosition() == position || existingEntry.getEntityId() == entityId
      }

      if (WorldUtils.canMineBlock(position)) {
         entries.add(BlockBreakEntry(entityId, position, extraBreak))
      }
   }

   @EventHandler
   private fun onConnectScreenConnectEvent(event: ConnectScreenConnectEvent) {
      entries.clear()
   }

   @EventHandler
   private fun onPacketReceive(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundBlockDestructionPacket) {
         val destructionPacket = event.packet as ClientboundBlockDestructionPacket
         ClientAccess.collectiveMinecraft.execute {
            if (destructionPacket.progress >= 0) {
               val breakStage = destructionPacket.progress

               if (breakStage <= 10) {
                  val positionSnapshot = destructionPacket.pos
                  val breakerId = destructionPacket.id
                  trackBlockBreaking(positionSnapshot, breakerId)
               }
            }
         }
      }

      if (event.packet is ClientboundBlockUpdatePacket) {
         val updatePacket = event.packet as ClientboundBlockUpdatePacket
         val updatedState: BlockState = updatePacket.blockState
         if (updatedState.isAir) {
            val removedPosition = updatePacket.pos
            onBlockRemoved(removedPosition)
         }
      }

      if (event.packet is ClientboundSectionBlocksUpdatePacket) {
         val sectionPacket = event.packet as ClientboundSectionBlocksUpdatePacket
         sectionPacket.runUpdates { position, updatedState ->
            if (updatedState.isAir) {
               onBlockRemoved(position)
            }
         }
      }
   }

   fun getBreakProgress(position: BlockPos): Float {
      if (!WorldUtils.canMineBlock(position)) {
         return 0.0f
      }

      return entries.stream()
         .filter { entry -> entry.getPosition() == position }
         .map(BlockBreakEntry::getProgress)
         .findAny().orElse(0.0f)
   }

   companion object {
      private var speedMine: SpeedMineModule? = null
      private var miningPickaxe: ItemStack? = null

      private fun getMiningPickaxe(): ItemStack {
         var pickaxe = miningPickaxe
         if (pickaxe == null) {
            pickaxe = ItemStack(Items.NETHERITE_PICKAXE)
            val efficiency: ResourceKey<net.minecraft.world.item.enchantment.Enchantment> = Enchantments.EFFICIENCY
            val enchantment: Holder<net.minecraft.world.item.enchantment.Enchantment>? = EnchantmentUtils.getHolder(efficiency)

            pickaxe.enchant(enchantment!!, 5)
            miningPickaxe = pickaxe
         }

         return pickaxe
      }

      @JvmStatic
      fun bindModules(modules: ModuleRegistry) {
         speedMine = modules.getModule(
            SpeedMineModule::class.java
         ) as SpeedMineModule
      }
   }
}
